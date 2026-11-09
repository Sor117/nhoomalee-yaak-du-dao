package ocs.service;

import ocs.domain.OcsException;
import ocs.domain.SciencePlan;
import ocs.domain.SciencePlanRepository;

/**
 * Auxiliary program required by the Submit Science Plan narrative.
 */
public final class MarkPlanTestedService {
    private final SciencePlanRepository planRepository;

    public MarkPlanTestedService(SciencePlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    public SciencePlan markPlanTested(int planId) {
        SciencePlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new OcsException(
                        "No science plan found with ID " + planId + "."));
        plan.markTested();
        return planRepository.save(plan);
    }
}
