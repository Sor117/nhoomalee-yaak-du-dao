package ocs.domain;

public enum Quadrant {
    NQ1,
    NQ2,
    NQ3,
    NQ4,
    SQ1,
    SQ2,
    SQ3,
    SQ4;

    public boolean isNorth() {
        return name().startsWith("NQ");
    }

}
