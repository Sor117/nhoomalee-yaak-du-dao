package ocs.service;

import java.util.List;

import ocs.domain.ObservingProgram;
import ocs.domain.ObservingProgramDraft;
import ocs.domain.ObservingProgramRepository;
import ocs.domain.OcsException;
import ocs.domain.PlanStatus;
import ocs.domain.SciencePlan;
import ocs.domain.SciencePlanRepository;

/**
 * Use case 4: transform a validated science plan into an observing program.
 */
public final class CreateObservingProgramService {
    private final SciencePlanRepository planRepository;
    private final ObservingProgramRepository programRepository;

    public CreateObservingProgramService(SciencePlanRepository planRepository, ObservingProgramRepository programRepository) {
        this.planRepository = planRepository;
        this.programRepository = programRepository;
    }

    public List<SciencePlan> listValidatedPlans() {
        return planRepository.findByStatus(PlanStatus.VALIDATED);
    }

    public ObservingProgram createProgram(int planId, String observerId, ObservingProgramDraft draft) {
        SciencePlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new OcsException(
                        "No science plan found with ID " + planId + "."));
        if (plan.getStatus() != PlanStatus.VALIDATED) {
            throw new OcsException("The science plan must be validated before an observing program can be created.");
        }
        if (!draft.getOptics().isWithinRanges(plan.getSchedule().getTelescope())) {
            throw new OcsException("The optics values are outside the supported ranges for this telescope.");
        }
        if (!draft.getMechanism().isWithinRanges()) {
            throw new OcsException("The mechanism values are outside the supported ranges.");
        }
        ObservingProgram program = new ObservingProgram(
                programRepository.nextId(), plan, observerId, draft);
        return programRepository.save(program);
    }
}
