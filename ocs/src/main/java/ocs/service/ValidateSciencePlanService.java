package ocs.service;

import java.util.List;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import ocs.domain.OcsException;
import ocs.domain.PlanStatus;
import ocs.domain.SciencePlan;
import ocs.domain.SciencePlanRepository;
import ocs.validation.PlanValidator;

/**
 * Use case 3: validate a submitted science plan with the seven Strategy rules.
 */
public final class ValidateSciencePlanService {
    private final SciencePlanRepository planRepository;
    private final PlanValidator planValidator;

    public ValidateSciencePlanService(SciencePlanRepository planRepository, PlanValidator planValidator) {
        this.planRepository = planRepository;
        this.planValidator = planValidator;
    }

    public List<SciencePlan> listSubmittedPlans() {
        return planRepository.findByStatus(PlanStatus.SUBMITTED);
    }

    public List<String> validatePlan(int planId, String observerName) {
        SciencePlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new OcsException(
                        "No science plan found with ID " + planId + "."));
        if (plan.getStatus() != PlanStatus.SUBMITTED) {
            throw new OcsException("Only a submitted science plan can be validated.");
        }
        List<String> failures = planValidator.validate(plan);
        if (failures.isEmpty()) {
            plan.markValidated(observerName, LocalDateTime.now(ZoneOffset.UTC));
        } else {
            plan.markInvalidated(observerName, LocalDateTime.now(ZoneOffset.UTC));
        }
        planRepository.save(plan);
        return failures;
    }
}
