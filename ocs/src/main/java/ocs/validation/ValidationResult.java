package ocs.validation;

public final class ValidationResult {
    private final String ruleName;
    private final boolean passed;

    public ValidationResult(String ruleName, boolean passed) {
        this.ruleName = ruleName;
        this.passed = passed;
    }

    public String getRuleName() {
        return ruleName;
    }

    public boolean isPassed() {
        return passed;
    }
}
