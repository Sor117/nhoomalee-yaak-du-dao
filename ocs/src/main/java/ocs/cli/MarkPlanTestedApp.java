package ocs.cli;

import ocs.domain.SciencePlan;
import ocs.service.MarkPlanTestedService;

public final class MarkPlanTestedApp extends AbstractCliApp {
    private final MarkPlanTestedService service;

    public MarkPlanTestedApp(MarkPlanTestedService service) {
        this.service = service;
    }

    @Override
    protected void execute(ArgumentValues values) {
        SciencePlan plan = service.markPlanTested(values.getInt("plan"));
        print("Science plan ID " + plan.getPlanId() + " is now "
                + plan.getStatus() + ".");
    }
}
