package ocs.validation;

import ocs.domain.SciencePlan;

public final class ObservationMonthRule implements ValidationRule {
    @Override
    public String name() {
        return "The start date must fall in a month in which the target is observable.";
    }

    @Override
    public boolean check(SciencePlan plan) {
        return plan.getTarget().isObservableIn(plan.getSchedule().getStartDate().getMonth());
    }
}
