package ocs.validation;

import ocs.domain.SciencePlan;

/**
 * Concrete strategy for the rule: Objective must not be blank.
 */
public final class ObjectiveNotBlankRule implements ValidationRule {
    @Override
    public String name() {
        return "Objective must not be blank.";
    }

    @Override
    public boolean check(SciencePlan plan) {
        return plan.getObjective() != null && !plan.getObjective().isBlank();
    }
}
