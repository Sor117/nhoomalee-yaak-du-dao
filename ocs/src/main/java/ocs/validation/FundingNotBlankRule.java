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
    public ValidationResult check(SciencePlan plan) {
        return new ValidationResult(name(), plan.getFunding() != null);
    }
}
