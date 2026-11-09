package ocs.domain;

public final class DataProcessingSettings {
    private final FileType fileType;
    private final FileQuality fileQuality;
    private final ColorType colorType;
    private final double contrast;
    private final double exposure;
    private final ColorAdjustments colorAdjustments;
    private final ToneAdjustments toneAdjustments;

    public DataProcessingSettings(FileType fileType, FileQuality fileQuality, ColorType colorType, double contrast, double exposure, ColorAdjustments colorAdjustments, ToneAdjustments toneAdjustments) {
        this.fileType = fileType;
        this.fileQuality = fileQuality;
        this.colorType = colorType;
        this.contrast = contrast;
        this.exposure = exposure;
        this.colorAdjustments = colorAdjustments;
        this.toneAdjustments = toneAdjustments;
    }

    public FileType getFileType() {
        return fileType;
    }

    public FileQuality getFileQuality() {
        return fileQuality;
    }

    public ColorType getColorType() {
        return colorType;
    }

    public double getContrast() {
        return contrast;
    }

    public double getExposure() {
        return exposure;
    }

    public ColorAdjustments getColorAdjustments() {
        return colorAdjustments;
    }

    public ToneAdjustments getToneAdjustments() {
        return toneAdjustments;
    }

    public boolean isCompleteForMode() {
        if (colorType == ColorType.COLOR) {
            return colorAdjustments != null;
        }
        return toneAdjustments != null;
    }
}
