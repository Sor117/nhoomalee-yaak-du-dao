package ocs.cli;

import ocs.domain.OcsException;
import ocs.domain.SciencePlan;
import ocs.domain.SciencePlanRepository;

/**
 * Auxiliary program required by the Submit Science Plan narrative: it moves a tested plan
 * from CREATED to TESTED.
 */
public final class MarkPlanTestedApp extends AbstractCliApp {
    private final SciencePlanRepository planRepository;

    public MarkPlanTestedApp(SciencePlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    protected void execute(ArgumentValues values) {
        int planId = values.getInt("plan");
        SciencePlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new OcsException(
                        "No science plan found with ID " + planId + "."));
        plan.markTested();
        planRepository.save(plan);
        print("Science plan ID " + plan.getPlanId() + " is now "
                + plan.getStatus() + ".");
    }
}
