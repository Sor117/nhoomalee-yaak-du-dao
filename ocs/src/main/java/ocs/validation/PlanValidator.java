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

    public List<String> validate(SciencePlan plan) {
        List<String> failures = new ArrayList<>();
        for (ValidationRule rule : rules) {
            if (!rule.check(plan)) {
                failures.add(rule.name());
            }
        }
        return List.copyOf(failures);
    }
}
