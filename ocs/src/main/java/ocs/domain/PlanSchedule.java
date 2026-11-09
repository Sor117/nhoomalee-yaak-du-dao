package ocs.domain;

import java.time.LocalDate;

public final class PlanSchedule {
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final TelescopeSite telescope;

    public PlanSchedule(LocalDate startDate, LocalDate endDate, TelescopeSite telescope) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.telescope = telescope;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public TelescopeSite getTelescope() {
        return telescope;
    }

    public boolean isOrdered() {
        return !startDate.isAfter(endDate);
    }

    public boolean overlaps(PlanSchedule other) {
        if (telescope != other.getTelescope()) {
            return false;
        }
        return !startDate.isAfter(other.getEndDate())
                && !other.getStartDate().isAfter(endDate);
    }
}
