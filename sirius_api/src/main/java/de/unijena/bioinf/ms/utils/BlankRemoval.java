package de.unijena.bioinf.ms.utils;

import de.unijena.bioinf.ChemistryBase.chem.RetentionTime;
import de.unijena.bioinf.ChemistryBase.ms.*;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BlankRemoval {

    public static MzRTPeak[] readFeatureTable(BufferedReader reader) throws IOException {
        String header = reader.readLine();
        String sep = "\\s";
        //assert columns
        String[] cols = header.split(sep);
        if (cols.length!=3){
            sep = ",";
            cols = header.split(sep);
            if (cols.length!=3)
                throw new RuntimeException("Feature table must contain 3 columns: rt, mz, intensity");
        }
        if (!cols[0].toLowerCase().contains("rt")
            || (!cols[1].equalsIgnoreCase("mz") && (!cols[1].equalsIgnoreCase("mass")))
            || (!cols[2].equalsIgnoreCase("intensity") && (!cols[2].equalsIgnoreCase("int")))){
            throw new RuntimeException("Feature table must contain 3 columns and header: rt, mz, intensity");
        }

        List<MzRTPeak> features = new ArrayList<>();
        String line;
        try {
            while ((line=reader.readLine())!=null){
                cols = line.split(sep);
                MzRTPeak peak = new MzRTPeak(Double.parseDouble(cols[0]), Double.parseDouble(cols[1]), Double.parseDouble(cols[2]));
                features.add(peak);
            }
        } catch (Exception e){
            throw new IOException(e);
        }
        return features.toArray(new MzRTPeak[0]);
    }

    private final MzRTPeak[] blankFeatures;
    private final Deviation maxMzDeviation;
    private final double maxRetentionTimeShift;
    private final double minFoldChange;
    private final Deviation findParentPeakInMs1Deviation;
    /**
     *
     * @param blankFeatures
     * @param maxMzDeviation
     * @param maxRetentionTimeShift
     * @param minFoldChange minimum intensity fold change to except compound as real feature
     */
    public BlankRemoval(MzRTPeak[] blankFeatures, Deviation maxMzDeviation, double maxRetentionTimeShift, double minFoldChange, Deviation findParentPeakInMs1Deviation) {
        this.blankFeatures = Arrays.copyOf(blankFeatures, blankFeatures.length);
        this.maxMzDeviation = maxMzDeviation;
        this.maxRetentionTimeShift = maxRetentionTimeShift;
        this.minFoldChange = minFoldChange;
        this.findParentPeakInMs1Deviation = findParentPeakInMs1Deviation;

        Arrays.sort(this.blankFeatures);

        assertSorted();
    }

    public BlankRemoval(MzRTPeak[] blankFeatures, Deviation maxMzDeviation, double maxRetentionTimeShift, double minFoldChange) {
        this(blankFeatures, maxMzDeviation, maxRetentionTimeShift, minFoldChange, new Deviation(100, 0.1));
    }

    private boolean assertSorted(){
        double mz = Double.NEGATIVE_INFINITY;
        for (MzRTPeak blankFeature : blankFeatures) {
            if (blankFeature.getMass()<mz){
                LoggerFactory.getLogger(BlankRemoval.class).error("features not sorted");
                return false;
            }
            mz = blankFeature.getMass();
        }
        return true;
    }

    public List<Ms2Experiment> removeBlanks(List<Ms2Experiment> experiments) {
        List<Ms2Experiment> realFeatures = new ArrayList<>();
        for (Ms2Experiment experiment : experiments) {
            boolean isFeature = isRealFeature(experiment);
            if (isFeature) realFeatures.add(experiment);
        }
        return realFeatures;
    }


    private boolean isRealFeature(Ms2Experiment experiment){
        double mz = experiment.getIonMass();
        double intensity = CompoundFilterUtil.getFeatureIntensity(experiment, findParentPeakInMs1Deviation);
        double rt = experiment.hasAnnotation(RetentionTime.class)?experiment.getAnnotation(RetentionTime.class).getRetentionTimeInSeconds():Double.NaN;

        //todo test Nan
        MzRTPeak peak = new MzRTPeak(rt, mz, intensity);
        int idx = Arrays.binarySearch(blankFeatures, peak);
        if (idx<0){
            idx = -idx-1;
        }
        for (int i = idx; i < blankFeatures.length; i++) {
            MzRTPeak blankFeature = blankFeatures[i];
            if (!maxMzDeviation.inErrorWindow(blankFeature.getMass(),mz)){
                break;
            }
            if (Double.isNaN(rt) || Math.abs(rt-blankFeature.getRetentionTime())<maxRetentionTimeShift){
                if (intensity==0 || intensity<blankFeature.getIntensity()*minFoldChange){
                    //blank has higher intensity -> no feature
                    return false;
                }
            }
        }
        for (int i = idx - 1; i >= 0; i--) {
            MzRTPeak blankFeature = blankFeatures[i];
            if (!maxMzDeviation.inErrorWindow(blankFeature.getMass(),mz)){
                break;
            }
            if (Double.isNaN(rt) || Math.abs(rt-blankFeature.getRetentionTime())<maxRetentionTimeShift){
                if (intensity==0 || intensity<blankFeature.getIntensity()*minFoldChange){
                    //blank has higher intensity -> no feature
                    return false;
                }
            }
        }
        return true;
    }




}
