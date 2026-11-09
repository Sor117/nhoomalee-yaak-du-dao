package ocs.domain;

import java.math.BigDecimal;

public final class SciencePlanDraft {
    private final String planName;
    private final String creator;
    private final BigDecimal funding;
    private final String objective;
    private final PlanSchedule schedule;
    private final String targetName;
    private final DataProcessingSettings dataProcessingSettings;

    public SciencePlanDraft(String planName, String creator, BigDecimal funding, String objective, PlanSchedule schedule, String targetName, DataProcessingSettings dataProcessingSettings) {
        this.planName = planName;
        this.creator = creator;
        this.funding = funding;
        this.objective = objective;
        this.schedule = schedule;
        this.targetName = targetName;
        this.dataProcessingSettings = dataProcessingSettings;
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

    public String getTargetName() {
        return targetName;
    }

    public DataProcessingSettings getDataProcessingSettings() {
        return dataProcessingSettings;
    }
}
