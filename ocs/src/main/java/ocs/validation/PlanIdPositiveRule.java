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
    public boolean check(SciencePlan plan) {
        return plan.getPlanId() > 0;
    }
}
