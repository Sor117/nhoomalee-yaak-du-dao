package ocs.validation;

import ocs.domain.SciencePlan;

/**
 * Strategy: one independent validation criterion of the Validate a science plan use case.
 */
public interface ValidationRule {
    public String name();
    public ValidationResult check(SciencePlan plan);
}
