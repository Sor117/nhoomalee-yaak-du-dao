package ocs.domain;

import java.util.List;

public final class ObservingProgram {
    private final int programId;
    private final SciencePlan sciencePlan;
    private final String createdBy;
    private final OpticsConfiguration optics;
    private final MechanismConfiguration mechanism;
    private final List<TelePositionPair> telePositionPairs;
    private ObservingProgramStatus status;

    public ObservingProgram(int programId, SciencePlan sciencePlan, String createdBy, ObservingProgramDraft draft) {
        this.programId = programId;
        this.sciencePlan = sciencePlan;
        this.createdBy = createdBy;
        this.optics = draft.getOptics();
        this.mechanism = draft.getMechanism();
        this.telePositionPairs = List.copyOf(draft.getTelePositionPairs());
        this.status = ObservingProgramStatus.CREATED;
    }

    public int getProgramId() {
        return programId;
    }

    public SciencePlan getSciencePlan() {
        return sciencePlan;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public OpticsConfiguration getOptics() {
        return optics;
    }

    public MechanismConfiguration getMechanism() {
        return mechanism;
    }

    public List<TelePositionPair> getTelePositionPairs() {
        return telePositionPairs;
    }

    public ObservingProgramStatus getStatus() {
        return status;
    }

    public TelescopeSite getGeminiLocation() {
        return sciencePlan.getSchedule().getTelescope();
    }

    public void restoreStatus(ObservingProgramStatus restoredStatus) {
        this.status = restoredStatus;
    }
}
