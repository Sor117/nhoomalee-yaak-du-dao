package ocs.validation;

import ocs.domain.SciencePlan;

/**
 * Concrete strategy for the rule: Creator must not be blank.
 */
public final class CreatorNotBlankRule implements ValidationRule {
    @Override
    public String name() {
        return "Creator must not be blank.";
    }

    @Override
    public boolean check(SciencePlan plan) {
        return plan.getCreator() != null && !plan.getCreator().isBlank();
    }
}
