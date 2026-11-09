package ocs.domain;

public final class TelePositionPair {
    private final Direction direction;
    private final double degree;

    public TelePositionPair(Direction direction, double degree) {
        this.direction = direction;
        this.degree = degree;
    }

    public Direction getDirection() {
        return direction;
    }

    public double getDegree() {
        return degree;
    }
}
