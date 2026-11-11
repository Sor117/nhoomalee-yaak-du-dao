package ocs.validation;

import ocs.domain.SciencePlan;

/**
 * Concrete strategy for the rule: Funding must not be blank.
 */
public final class FundingNotBlankRule implements ValidationRule {
    @Override
    public String name() {
        return "Funding must not be blank.";
    }

    @Override
    public boolean check(SciencePlan plan) {
        return plan.getFunding() != null;
    }
}
