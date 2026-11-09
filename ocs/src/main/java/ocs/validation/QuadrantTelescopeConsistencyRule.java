package ocs.validation;

import ocs.domain.Quadrant;
import ocs.domain.SciencePlan;
import ocs.domain.TelescopeSite;

public final class QuadrantTelescopeConsistencyRule implements ValidationRule {
    @Override
    public String name() {
        return "The telescope must match the quadrant of the target.";
    }

    @Override
    public ValidationResult check(SciencePlan plan) {
        TelescopeSite required = plan.getTarget().getQuadrant().isNorth()
                ? TelescopeSite.GEMINI_NORTH
                : TelescopeSite.GEMINI_SOUTH;
        return new ValidationResult(name(), plan.getSchedule().getTelescope() == required);
    }
}
