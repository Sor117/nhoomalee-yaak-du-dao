package ocs.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class SciencePlan {
    private final int planId;
    private final String planName;
    private final String creator;
    private final BigDecimal funding;
    private final String objective;
    private final PlanSchedule schedule;
    private final StarSystemInfo target;
    private final DataProcessingSettings dataProcessingSettings;
    private PlanStatus status;
    private String validatedBy;
    private LocalDateTime validatedAt;

    public SciencePlan(int planId, SciencePlanDraft draft, StarSystemInfo target) {
        this.planId = planId;
        this.planName = draft.getPlanName();
        this.creator = draft.getCreator();
        this.funding = draft.getFunding();
        this.objective = draft.getObjective();
        this.schedule = draft.getSchedule();
        this.target = target;
        this.dataProcessingSettings = draft.getDataProcessingSettings();
        this.status = PlanStatus.CREATED;
    }

    public int getPlanId() {
        return planId;
    }

    public String getPlanName() {
        return planName;
    }

    public String getCreator() {
        return creator;
    }

    public BigDecimal getFunding() {
        return funding;
    }

    public String getObjective() {
        return objective;
    }

    public PlanSchedule getSchedule() {
        return schedule;
    }

    public StarSystemInfo getTarget() {
        return target;
    }

    public DataProcessingSettings getDataProcessingSettings() {
        return dataProcessingSettings;
    }

    public PlanStatus getStatus() {
        return status;
    }

    public String getValidatedBy() {
        return validatedBy;
    }

    public LocalDateTime getValidatedAt() {
        return validatedAt;
    }

    public void markTested() {
        if (status != PlanStatus.CREATED) {
            throw new OcsException("Only a created science plan can be marked as tested.");
        }
        status = PlanStatus.TESTED;
    }

    public void submit() {
        if (status != PlanStatus.TESTED) {
            throw new OcsException("This science plan is incomplete. Please complete and test it before submission.");
        }
        status = PlanStatus.SUBMITTED;
    }

    public void markValidated(String observerName, LocalDateTime validatedAtUtc) {
        status = PlanStatus.VALIDATED;
        validatedBy = observerName;
        validatedAt = validatedAtUtc;
    }

    public void markInvalidated(String observerName, LocalDateTime validatedAtUtc) {
        status = PlanStatus.INVALIDATED;
        validatedBy = observerName;
        validatedAt = validatedAtUtc;
    }

    public void restoreState(PlanStatus status, String validatedBy, LocalDateTime validatedAt) {
        this.status = status;
        this.validatedBy = validatedBy;
        this.validatedAt = validatedAt;
    }
}
