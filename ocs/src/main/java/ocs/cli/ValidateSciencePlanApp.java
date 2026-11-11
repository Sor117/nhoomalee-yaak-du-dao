package ocs.cli;

import java.util.List;

import ocs.domain.SciencePlan;
import ocs.service.ValidateSciencePlanService;

public final class ValidateSciencePlanApp extends AbstractCliApp {
    private final ValidateSciencePlanService service;

    public ValidateSciencePlanApp(ValidateSciencePlanService service) {
        this.service = service;
    }

    @Override
    protected void execute(ArgumentValues values) {
        List<SciencePlan> submitted = service.listSubmittedPlans();
        print("Submitted science plans: " + submitted.size() + " found.");
        for (SciencePlan plan : submitted) {
            print("  " + plan.getPlanId() + " | " + plan.getPlanName()
                    + " | " + plan.getTarget().getConstellation());
        }
        int planId = values.getInt("plan");
        String observer = values.getString("observer");
        List<String> failures = service.validatePlan(planId, observer);
        if (failures.isEmpty()) {
            print("Validate Science Plan Succeed ID: " + planId);
        } else {
            print("Validate failed: " + String.join(", ", failures));
        }
    }
}
