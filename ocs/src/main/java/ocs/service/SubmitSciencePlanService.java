package ocs.service;

import java.util.ArrayList;
import java.util.List;

import ocs.domain.OcsException;
import ocs.domain.PlanStatus;
import ocs.domain.SciencePlan;
import ocs.domain.SciencePlanRepository;

/**
 * Use case 2: list the astronomer's plans and submit a tested one.
 */
public final class SubmitSciencePlanService {
    private final SciencePlanRepository planRepository;

    public SubmitSciencePlanService(SciencePlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    public List<SciencePlan> listPlans(String creator) {
        return planRepository.findByCreator(creator);
    }

    public List<SciencePlan> filterPlansByStatus(String creator, PlanStatus status) {
        List<SciencePlan> filtered = new ArrayList<>();
        for (SciencePlan plan : planRepository.findByCreator(creator)) {
            if (plan.getStatus() == status) {
                filtered.add(plan);
            }
        }
        return List.copyOf(filtered);
    }

    public SciencePlan submitPlan(int planId, String creator) {
        SciencePlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new OcsException(
                        "No science plan found with ID " + planId + "."));
        if (!plan.getCreator().equals(creator)) {
            throw new OcsException("This science plan belongs to another astronomer.");
        }
        if (plan.getStatus() == PlanStatus.SUBMITTED
                || plan.getStatus() == PlanStatus.VALIDATED
                || plan.getStatus() == PlanStatus.RUNNING) {
            throw new OcsException("This science plan has already been submitted or executed.");
        }
        if (plan.getStatus() != PlanStatus.TESTED) {
            throw new OcsException("This science plan is incomplete. Please complete and test it before submission.");
        }
        plan.submit();
        return planRepository.save(plan);
    }
}
