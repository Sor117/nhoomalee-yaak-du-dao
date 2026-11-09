package ocs.validation;

import ocs.domain.SciencePlan;

/**
 * Concrete strategy for the rule: Plan ID must be a positive integer.
 */
public final class PlanIdPositiveRule implements ValidationRule {
    @Override
    public String name() {
        return "Plan ID must be a positive integer.";
    }

    @Override
    public ValidationResult check(SciencePlan plan) {
        return new ValidationResult(name(), plan.getPlanId() > 0);
    }
}
