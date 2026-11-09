package ocs.validation;

import java.util.ArrayList;
import java.util.List;

public final class ValidationOutcome {
    private final List<ValidationResult> results;

    public ValidationOutcome(List<ValidationResult> results) {
        this.results = List.copyOf(results);
    }

    public boolean isValid() {
        return results.stream().allMatch(ValidationResult::isPassed);
    }

    public List<String> getFailures() {
        List<String> failures = new ArrayList<>();
        for (ValidationResult result : results) {
            if (!result.isPassed()) {
                failures.add(result.getRuleName());
            }
        }
        return List.copyOf(failures);
    }
}
