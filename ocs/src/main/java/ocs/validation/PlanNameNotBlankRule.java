package ocs.validation;

import ocs.domain.SciencePlan;

/**
 * Concrete strategy for the rule: Plan Name must not be blank.
 */
public final class PlanNameNotBlankRule implements ValidationRule {
    @Override
    public String name() {
        return "Plan Name must not be blank.";
    }

    @Override
    public ValidationResult check(SciencePlan plan) {
        return new ValidationResult(name(), plan.getPlanName() != null && !plan.getPlanName().isBlank());
    }
}
