package de.unijena.bioinf.IsotopePatternAnalysis;

import de.unijena.bioinf.ms.annotations.Ms2ExperimentAnnotation;
import de.unijena.bioinf.ms.properties.DefaultProperty;

public class IsotopicIntensitySettings implements Ms2ExperimentAnnotation {

    /**
     * Ignore isotope peaks below this intensity.
     * This value should reflect the smallest relative intensive which is still above noise level.
     * Obviously, this is hard to judge without having absolute values. Keeping this value around 1% is
     * fine for most settings. Set it to smaller values if you trust your small intensities.
     */
    @DefaultProperty(propertyParent = "ms1", propertyKey = "minimalIntensityToConsider")
    public final double minimalIntensityToConsider;

    /**
     * The average absolute deviation between theoretical and measured intensity of isotope peaks.
     * Do not change this parameter without a good reason!
     */
    @DefaultProperty(propertyParent = "ms1", propertyKey = "absoluteIntensityError")
    public final double absoluteIntensityError;

    /**
     * The average relative deviation between theoretical and measured intensity of isotope peaks.
     * Do not change this parameter without a good reason!
     */
    @DefaultProperty(propertyParent = "ms1", propertyKey = "relativeIntensityError")
    public final double relativeIntensityError;

    public IsotopicIntensitySettings(double minimalIntensityToConsider, double absoluteIntensityError, double relativeIntensityError) {
        this.minimalIntensityToConsider = minimalIntensityToConsider;
        this.absoluteIntensityError = absoluteIntensityError;
        this.relativeIntensityError = relativeIntensityError;
    }

    private IsotopicIntensitySettings() {
        this(Double.NaN,Double.NaN,Double.NaN);
    }

    public double getMinimalIntensityToConsider() {
        return minimalIntensityToConsider;
    }

    public double getAbsoluteIntensityError() {
        return absoluteIntensityError;
    }

}
