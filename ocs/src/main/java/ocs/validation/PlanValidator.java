package ocs.validation;

import java.util.ArrayList;
import java.util.List;

import ocs.domain.SciencePlan;

/**
 * Context of the Strategy pattern.
 */
public final class PlanValidator {
    private final List<ValidationRule> rules;

    public PlanValidator(List<ValidationRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public ValidationOutcome validate(SciencePlan plan) {
        List<ValidationResult> results = new ArrayList<>();
        for (ValidationRule rule : rules) {
            results.add(rule.check(plan));
        }
        return new ValidationOutcome(results);
    }
}
