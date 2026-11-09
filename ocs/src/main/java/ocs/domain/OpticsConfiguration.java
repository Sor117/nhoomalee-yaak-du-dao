package ocs.domain;

public final class OpticsConfiguration {
    private final OpticsPrimary opticsPrimary;
    private final double fStop;
    private final double opticsSecondaryRmsNm;

    public OpticsConfiguration(OpticsPrimary opticsPrimary, double fStop, double opticsSecondaryRmsNm) {
        this.opticsPrimary = opticsPrimary;
        this.fStop = fStop;
        this.opticsSecondaryRmsNm = opticsSecondaryRmsNm;
    }

    public OpticsPrimary getOpticsPrimary() {
        return opticsPrimary;
    }

    public double getFStop() {
        return fStop;
    }

    public double getOpticsSecondaryRmsNm() {
        return opticsSecondaryRmsNm;
    }

    public boolean isWithinRanges(TelescopeSite site) {
        boolean fStopOk = opticsPrimary == OpticsPrimary.GNZ
                ? fStop >= 1.8 && fStop <= 8.1
                : fStop >= 2.9 && fStop <= 18.0;
        boolean rmsOk = site == TelescopeSite.GEMINI_SOUTH
                ? opticsSecondaryRmsNm >= 5.0 && opticsSecondaryRmsNm <= 13.0
                : opticsSecondaryRmsNm >= 5.0 && opticsSecondaryRmsNm <= 17.0;
        return fStopOk && rmsOk;
    }
}
