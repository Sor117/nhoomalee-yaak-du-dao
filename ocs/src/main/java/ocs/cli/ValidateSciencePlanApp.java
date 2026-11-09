package ocs.cli;

import java.util.List;

import ocs.domain.SciencePlan;
import ocs.service.ValidateSciencePlanService;
import ocs.validation.ValidationOutcome;

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
        ValidationOutcome outcome = service.validatePlan(planId, observer);
        if (outcome.isValid()) {
            print("Validate Science Plan Succeed ID: " + planId);
        } else {
            print("Validate failed: " + String.join("; ", outcome.getFailures()));
        }
    }
}
