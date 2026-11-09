package ocs.cli;

import java.util.List;
import java.util.Locale;

import ocs.domain.PlanStatus;
import ocs.domain.SciencePlan;
import ocs.service.SubmitSciencePlanService;

public final class SubmitSciencePlanApp extends AbstractCliApp {
    private final SubmitSciencePlanService service;

    public SubmitSciencePlanApp(SubmitSciencePlanService service) {
        this.service = service;
    }

    @Override
    protected void execute(ArgumentValues values) {
        String creator = values.getString("creator");
        List<SciencePlan> plans = service.listPlans(creator);
        print("Science plans of " + creator + ": " + plans.size() + " found.");
        for (SciencePlan plan : plans) {
            print("  " + plan.getPlanId() + " | " + plan.getPlanName()
                    + " | " + plan.getStatus());
        }
        if (values.has("status")) {
            PlanStatus status = PlanStatus.valueOf(
                    values.getString("status").toUpperCase(Locale.ROOT));
            for (SciencePlan plan : service.filterPlansByStatus(creator, status)) {
                print("  filtered " + plan.getPlanId() + " | "
                        + plan.getPlanName());
            }
        }
        if (!values.has("confirm")) {
            print("Submission cancelled. The science plan has not been submitted.");
            return;
        }
        SciencePlan submitted = service.submitPlan(values.getInt("plan"), creator);
        print("Science plan ID " + submitted.getPlanId() + " is now "
                + submitted.getStatus() + ".");
    }
}
