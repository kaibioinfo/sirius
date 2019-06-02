package de.unijena.bioinf.lcms;

import de.unijena.bioinf.ChemistryBase.ms.Deviation;
import de.unijena.bioinf.ChemistryBase.ms.utils.SimpleSpectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.Spectrums;
import de.unijena.bioinf.model.lcms.*;
import gnu.trove.list.array.TDoubleArrayList;

import java.util.Optional;

public class ChromatogramBuilder {

    protected final LCMSRun lcms;
    protected final NoiseModel noiseModel;
    protected final SpectrumStorage storage;
    protected final Deviation dev;

    protected ChromatogramCache cache;


    public ChromatogramBuilder(LCMSRun run, NoiseModel noiseModel, SpectrumStorage storage) {
        this.lcms = run;
        this.noiseModel = noiseModel;
        this.storage = storage;
        this.dev = new Deviation(10);
        cache = null;
    }

    public ChromatogramCache getCache() {
        return cache;
    }

    public void setCache(ChromatogramCache cache) {
        this.cache = cache;
    }

    public Optional<ChromatographicPeak> detectExact(Scan startingPoint, double mz) {
        final SimpleSpectrum spectrum = storage.getScan(startingPoint);
        int i = Spectrums.binarySearch(spectrum, mz, dev);
        if (i>=0) {
            return buildTrace(spectrum, new ScanPoint(startingPoint, spectrum.getMzAt(i), spectrum.getIntensityAt(i)));
        } else {
            return Optional.empty(); // no chromatographic peak detected
        }
    }

    public Optional<ChromatographicPeak> detect(Scan startingPoint, double mz) {
        final SimpleSpectrum spectrum = storage.getScan(startingPoint);
        int i = Spectrums.mostIntensivePeakWithin(spectrum, mz, dev);
        if (i>=0) {
            return buildTrace(spectrum, new ScanPoint(startingPoint, spectrum.getMzAt(i), spectrum.getIntensityAt(i)));
        } else {
            return Optional.empty(); // no chromatographic peak detected
        }
    }

    private Optional<ChromatographicPeak> buildTrace(SimpleSpectrum spectrum, ScanPoint scanPoint) {
        if (cache!=null) {
            Optional<ChromatographicPeak> peak = cache.retrieve(scanPoint);
            if (peak.isPresent()) {
                return peak;
            }
        }
        final MutableChromatographicPeak rightTrace = new MutableChromatographicPeak();
        final MutableChromatographicPeak leftTrace = new MutableChromatographicPeak();
        rightTrace.extendRight(scanPoint);
        leftTrace.extendRight(scanPoint);
        // extend to the right
        for (Scan scan  : lcms.getScansAfter(scanPoint.getScanNumber()).values()) {
            if (!scan.isMsMs()) {
                if (tryToExtend(rightTrace, scan)) {
                    // go on!
                } else {
                    // cannot extend further...
                    break;
                }
            }
        }
        // extend to the left
        for (Scan scan  : lcms.getScansBefore(scanPoint.getScanNumber()).values()) {
            if (!scan.isMsMs()) {
                if (tryToExtend(leftTrace, scan)) {
                    // go on!
                } else {
                    // cannot extend further...
                    break;
                }
            }
        }
        MutableChromatographicPeak concat = MutableChromatographicPeak.concat(leftTrace, rightTrace);
        Extrema extrema = detectExtrema(concat);

        for (int k=0, n=extrema.numberOfExtrema(); k < n; ++k) {
            if (!extrema.isMinimum(k)) {
                final double intensity = extrema.extrema.get(k);
                final int apexIndex = extrema.getIndexAt(k);
                final int leftIndex, rightIndex;
                if (k > 0) {
                    leftIndex = extrema.getIndexAt(k-1);
                } else leftIndex = 0;

                if (k+1 < n) {
                    rightIndex = extrema.getIndexAt(k+1);
                } else rightIndex = concat.numberOfScans()-1;
                concat.addSegment(leftIndex, apexIndex, rightIndex);
            }
        }

        if (concat.segments.size()==0) {
            System.err.println("Noisy chromatogram.");
            return Optional.empty(); // just noise
        }

        concat.trimEdges();
        // if scanPoint is removed during trimming, this is not a real chromatogram
        if (concat.getScanPointForScanId(scanPoint.getScanNumber())==null) return Optional.empty();
        if (cache!=null) cache.add(concat);
        return Optional.of(concat);
    }

    private Extrema detectExtrema(MutableChromatographicPeak peak) {
        // if a chromatographic peak is very long, a single noise level might be problematic. We split it in
        // smaller subgroups. I would say 25 scans are enough to estimate a noise level. For each consecutive 25
        // scans we define a separate noise lel

        float[] noiseLevels = new float[peak.numberOfScans()];
        if (peak.numberOfScans()>=10){
            final TDoubleArrayList medianSlope = new TDoubleArrayList(peak.numberOfScans());
            final TDoubleArrayList intensityQuantile = new TDoubleArrayList();
            int k = 0;
            while (k < peak.numberOfScans()) {
                medianSlope.resetQuick();
                intensityQuantile.resetQuick();
                int start = k;
                int end = k + 25;
                if (end + 25 > peak.numberOfScans()) end = peak.numberOfScans();
                int middle = start + (end - start) / 2;
                double noiseLevel = noiseModel.getNoiseLevel(peak.getScanNumberAt(middle), peak.getMzAt(middle));
                for (int i=start; i < end; ++i) {
                    if (i>0) medianSlope.add(Math.abs(peak.getIntensityAt(i) - peak.getIntensityAt(i - 1)));
                    intensityQuantile.add(peak.getIntensityAt(i));
                }
                medianSlope.sort();
                intensityQuantile.sort();
                noiseLevel = Math.max(noiseLevel, medianSlope.getQuick((int)(medianSlope.size()*0.75)));
                noiseLevel = Math.max(noiseLevel, intensityQuantile.getQuick((int)(intensityQuantile.size()*0.05))/2d);
                for (int i=start; i < end; ++i) noiseLevels[i] = (float)noiseLevel;
                k+=end;
            }
        } else {
            for (int i=0; i < peak.numberOfScans(); ++i) {
                noiseLevels[i] = (float)noiseModel.getNoiseLevel(peak.getScanNumberAt(i), peak.getMzAt(i));
            }
        }

        final Extrema extrema = new Extrema();
        boolean minimum = true;
        for (int k=0; k < peak.numberOfScans()-1; ++k) {
            final double a = (k==0) ? 0 : peak.getIntensityAt(k-1);
            final double b = peak.getIntensityAt(k);
            final double c = peak.getIntensityAt(k+1);

            if ((b-a) < 0 && (b - c) < 0) {
                // minimum
                if (minimum) {
                    if (extrema.lastExtremumIntensity() > b)
                        extrema.replaceLastExtremum(k, b);
                } else if (extrema.lastExtremumIntensity() - b > noiseLevels[k]) {
                    extrema.addExtremum(k, b);
                    minimum = true;
                }
            } else if ((b-a)>0 && (b-c)>0) {
                // maximum
                if (minimum) {
                    if (b - extrema.lastExtremumIntensity() > noiseLevels[k]) {
                        extrema.addExtremum(k, b);
                        minimum = false;
                    }
                } else {
                    if (extrema.lastExtremumIntensity() < b) {
                        extrema.replaceLastExtremum(k, b);
                    }
                }
            }

        }

        if (extrema.isMinimum(0) || !extrema.valid()) {
            System.err.println("Strange");
        }
        // we expect a small number of extrema:
        for (int k=2; k < 20; ++k) {
            assert extrema.valid();
            if (extrema.numberOfExtrema()>=(k-1)) {
                if (!extrema.smooth(noiseLevels, k, k))
                    break;
            } else break;
        }
        return extrema;

    }

    private boolean tryToExtend(MutableChromatographicPeak trace, Scan scan) {
        final ScanPoint previous = trace.getRightEdge();
        final SimpleSpectrum spec = storage.getScan(scan);
        final double mz = previous.getMass();
        final double intensity = previous.getIntensity();
        final double mzStd = Math.pow(dev.absoluteFor(mz)/2d,2);
        final double intVar = 1d;
        final double noiseLevel = noiseModel.getNoiseLevel(scan.getScanNumber(),mz);
        final int start = Spectrums.indexOfFirstPeakWithin(spec, mz, dev);
        if (start < 0) return false;
        int end;
        for (end=start; end < spec.size(); ++end) {
            if (!dev.inErrorWindow(mz, spec.getMzAt(end)) || spec.getIntensityAt(end) < noiseLevel)
                break;
        }
        if (end-start == 1) {
            trace.extendRight(new ScanPoint(scan, spec.getMzAt(start), spec.getIntensityAt(start)));
            return true;
        } else if (end <= start) {
            return false;
        } else {
            int bestIndex=start; double bestScore = 0d;
            for (int k=start; k < end; ++k) {
                double sc = score(spec.getMzAt(k)-mz, Math.log((noiseLevel+spec.getIntensityAt(k))/(noiseLevel+intensity)), mzStd, intVar);
                if (sc > bestScore) {
                    bestIndex = k;
                    bestScore = sc;
                }
            }
            trace.extendRight(new ScanPoint(scan, spec.getMzAt(bestIndex), spec.getIntensityAt(bestIndex)));
            return true;
        }
    }

    private double score(double mzDiff, double intDiff, double mzVar, double intVar) {
        return Math.exp(-(mzDiff*mzDiff/(4*mzVar) + intDiff*intDiff/(4*intVar)) );
    }

}
