package de.unijena.bioinf.sirius.iondetection;

import de.unijena.bioinf.ChemistryBase.chem.ChemicalAlphabet;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.ChemistryBase.ms.*;
import de.unijena.bioinf.ChemistryBase.ms.utils.SimpleMutableSpectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.SimpleSpectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.Spectrums;
import de.unijena.bioinf.ms.annotations.Requires;
import de.unijena.bioinf.sirius.ProcessedInput;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Search for m/z delta in MS1
 */
@Requires(MergedMs1Spectrum.class)
public class DetectIonsFromMs1 implements AdductDetection {

    @Nullable
    @Override
    public PossibleAdducts detect(ProcessedInput processedInput, Set<PrecursorIonType> candidates) {
        MergedMs1Spectrum ms1 = processedInput.getAnnotationOrThrow(MergedMs1Spectrum.class);
        final MS1MassDeviation dev = processedInput.getAnnotationOrThrow(MS1MassDeviation.class);
        SimpleMutableSpectrum mutableSpectrum;
        Ms2Experiment exp = processedInput.getExperimentInformation();
        if (ms1.isCorrelated() && ms1.mergedSpectrum.size()>1) {
            mutableSpectrum = new SimpleMutableSpectrum(ms1.mergedSpectrum);
            Spectrums.filterIsotpePeaks(mutableSpectrum, new Deviation(100), 1, 2, 5, new ChemicalAlphabet());
        } else {
            if (exp.getMs1Spectra() != null && exp.getMs1Spectra().size()>0){
                SimpleSpectrum pks = Spectrums.selectSpectrumWithMostIntensePrecursor(exp.getMs1Spectra(), exp.getIonMass(), dev.allowedMassDeviation);
                mutableSpectrum = new SimpleMutableSpectrum(pks==null ? exp.getMs1Spectra().get(0) : pks);
            } else return null;
        }

        Spectrums.normalizeToMax(mutableSpectrum, 100d);
        Spectrums.applyBaseline(mutableSpectrum, 1d);
//        //changed
        Spectrums.filterIsotpePeaks(mutableSpectrum, dev.massDifferenceDeviation, 0.3, 1, 5, new ChemicalAlphabet());

        PrecursorIonType[] ionType = Spectrums.guessIonization(mutableSpectrum, exp.getIonMass(), dev.allowedMassDeviation, candidates.toArray(new PrecursorIonType[candidates.size()]));
        return new PossibleAdducts(ionType);
    }
}
