package ocs.domain;

import java.time.Month;

public final class StarSystemInfo {
    private final String constellation;
    private final Quadrant quadrant;
    private final Month availableMonth;

    public StarSystemInfo(String constellation, Quadrant quadrant, Month availableMonth) {
        this.constellation = constellation;
        this.quadrant = quadrant;
        this.availableMonth = availableMonth;
    }

    public String getConstellation() {
        return constellation;
    }

    public Quadrant getQuadrant() {
        return quadrant;
    }

    public Month getAvailableMonth() {
        return availableMonth;
    }

    public boolean isObservableIn(Month month) {
        return availableMonth == month;
    }
}
