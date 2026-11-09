package ocs.domain;

public final class MechanismConfiguration {
    private final double foldMirrorDegree;
    private final FoldMirrorType foldMirrorType;
    private final int moduleContent;
    private final CalibrationUnit calibrationUnit;
    private final LightType lightType;

    public MechanismConfiguration(double foldMirrorDegree, FoldMirrorType foldMirrorType, int moduleContent, CalibrationUnit calibrationUnit, LightType lightType) {
        this.foldMirrorDegree = foldMirrorDegree;
        this.foldMirrorType = foldMirrorType;
        this.moduleContent = moduleContent;
        this.calibrationUnit = calibrationUnit;
        this.lightType = lightType;
    }

    public double getFoldMirrorDegree() {
        return foldMirrorDegree;
    }

    public FoldMirrorType getFoldMirrorType() {
        return foldMirrorType;
    }

    public int getModuleContent() {
        return moduleContent;
    }

    public CalibrationUnit getCalibrationUnit() {
        return calibrationUnit;
    }

    public LightType getLightType() {
        return lightType;
    }

    public boolean isWithinRanges() {
        boolean foldOk = foldMirrorDegree >= 30.0 && foldMirrorDegree <= 45.0;
        boolean moduleOk = moduleContent >= 1 && moduleContent <= 4;
        return foldOk && moduleOk;
    }
}
