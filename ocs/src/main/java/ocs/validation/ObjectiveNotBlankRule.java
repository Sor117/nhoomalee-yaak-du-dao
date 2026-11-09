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
    public ValidationResult check(SciencePlan plan) {
        return new ValidationResult(name(), plan.getObjective() != null && !plan.getObjective().isBlank());
    }
}
