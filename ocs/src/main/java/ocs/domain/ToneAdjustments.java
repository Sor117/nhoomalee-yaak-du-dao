package ocs.domain;

public final class ToneAdjustments {
    private final double highlights;
    private final double shadows;
    private final double whites;
    private final double blacks;

    public ToneAdjustments(double highlights, double shadows, double whites, double blacks) {
        this.highlights = highlights;
        this.shadows = shadows;
        this.whites = whites;
        this.blacks = blacks;
    }

    public double getHighlights() {
        return highlights;
    }

    public double getShadows() {
        return shadows;
    }

    public double getWhites() {
        return whites;
    }

    public double getBlacks() {
        return blacks;
    }
}
