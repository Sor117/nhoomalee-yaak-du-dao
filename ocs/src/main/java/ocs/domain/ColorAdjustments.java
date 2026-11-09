package ocs.domain;

public final class ColorAdjustments {
    private final double brightness;
    private final double saturation;
    private final double luminance;
    private final double hue;

    public ColorAdjustments(double brightness, double saturation, double luminance, double hue) {
        this.brightness = brightness;
        this.saturation = saturation;
        this.luminance = luminance;
        this.hue = hue;
    }

    public double getBrightness() {
        return brightness;
    }

    public double getSaturation() {
        return saturation;
    }

    public double getLuminance() {
        return luminance;
    }

    public double getHue() {
        return hue;
    }
}
