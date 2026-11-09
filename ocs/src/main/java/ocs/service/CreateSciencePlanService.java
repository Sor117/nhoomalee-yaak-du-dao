package ocs.service;

import ocs.domain.DataProcessingSettings;
import ocs.domain.OcsException;
import ocs.domain.SciencePlan;
import ocs.domain.SciencePlanDraft;
import ocs.domain.SciencePlanRepository;
import ocs.domain.StarCatalog;
import ocs.domain.StarSystemInfo;

/**
 * Use case 1: create a science plan for a target from the star catalogue.
 */
public final class CreateSciencePlanService {
    private final StarCatalog starCatalog;
    private final SciencePlanRepository planRepository;

    public CreateSciencePlanService(StarCatalog starCatalog, SciencePlanRepository planRepository) {
        this.starCatalog = starCatalog;
        this.planRepository = planRepository;
    }

    public SciencePlan createPlan(SciencePlanDraft draft) {
        if (isBlank(draft.getPlanName()) || isBlank(draft.getCreator())
                || isBlank(draft.getObjective())) {
            throw new OcsException("Please complete all required fields before saving the science plan.");
        }
        if (draft.getFunding() == null || draft.getFunding().signum() <= 0) {
            throw new OcsException("Funding amount must be a positive numerical value.");
        }
        if (!draft.getSchedule().isOrdered()) {
            throw new OcsException("Start date cannot be after the end date.");
        }
        if (!draft.getDataProcessingSettings().isCompleteForMode()) {
            throw new OcsException("Please complete all required fields for the selected data processing mode.");
        }
        StarSystemInfo target = starCatalog.lookup(draft.getTargetName())
                .orElseThrow(() -> new OcsException(
                        "Selected target not found in the star catalogue. Please choose a valid target."));
        if (planRepository.existsOverlapping(draft.getSchedule())) {
            throw new OcsException("Selected date range conflicts with another scheduled plan for this telescope.");
        }
        if (planRepository.existsByName(draft.getPlanName())) {
            throw new OcsException("A science plan with this name already exists. Please use a different plan name.");
        }
        SciencePlan plan = new SciencePlan(planRepository.nextId(), draft, target);
        return planRepository.save(plan);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
