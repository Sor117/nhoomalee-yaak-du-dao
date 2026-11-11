package ocs.validation;

import ocs.domain.SciencePlan;
import ocs.domain.TelescopeSite;

public final class QuadrantTelescopeConsistencyRule implements ValidationRule {
    @Override
    public String name() {
        return "The telescope must match the quadrant of the target.";
    }

    @Override
    public boolean check(SciencePlan plan) {
        TelescopeSite required = plan.getTarget().getQuadrant().isNorth()
                ? TelescopeSite.GEMINI_NORTH
                : TelescopeSite.GEMINI_SOUTH;
        return plan.getSchedule().getTelescope() == required;
    }
}
