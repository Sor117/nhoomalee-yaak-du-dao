package ocs.persistence;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;
import java.util.Properties;

import ocs.domain.ColorAdjustments;
import ocs.domain.ColorType;
import ocs.domain.DataProcessingSettings;
import ocs.domain.FileQuality;
import ocs.domain.FileType;
import ocs.domain.PlanSchedule;
import ocs.domain.PlanStatus;
import ocs.domain.Quadrant;
import ocs.domain.SciencePlan;
import ocs.domain.SciencePlanDraft;
import ocs.domain.SciencePlanRepository;
import ocs.domain.StarSystemInfo;
import ocs.domain.TelescopeSite;
import ocs.domain.ToneAdjustments;

/**
 * File-backed science plan repository; the only code that knows the data store layout.
 */
public final class FileSciencePlanRepository implements SciencePlanRepository {
    private final PropertyFileStore store;

    public FileSciencePlanRepository() {
        this.store = new PropertyFileStore("science_plans");
    }

    @Override
    public int nextId() {
        int max = 0;
        for (String key : store.keys()) {
            if (key.chars().allMatch(Character::isDigit)) {
                max = Math.max(max, Integer.parseInt(key));
            }
        }
        return max + 1;
    }

    @Override
    public SciencePlan save(SciencePlan plan) {
        store.save(String.valueOf(plan.getPlanId()), toProperties(plan));
        return plan;
    }

    @Override
    public Optional<SciencePlan> findById(int planId) {
        String key = String.valueOf(planId);
        if (!store.contains(key)) {
            return Optional.empty();
        }
        return Optional.of(toPlan(store.load(key)));
    }

    @Override
    public List<SciencePlan> findByCreator(String creator) {
        List<SciencePlan> plans = new ArrayList<>();
        for (SciencePlan plan : loadAll()) {
            if (plan.getCreator().equals(creator)) {
                plans.add(plan);
            }
        }
        return List.copyOf(plans);
    }

    @Override
    public List<SciencePlan> findByStatus(PlanStatus status) {
        List<SciencePlan> plans = new ArrayList<>();
        for (SciencePlan plan : loadAll()) {
            if (plan.getStatus() == status) {
                plans.add(plan);
            }
        }
        return List.copyOf(plans);
    }

    @Override
    public boolean existsByName(String planName) {
        for (SciencePlan plan : loadAll()) {
            if (plan.getPlanName().equalsIgnoreCase(planName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean existsOverlapping(PlanSchedule schedule) {
        for (SciencePlan plan : loadAll()) {
            if (plan.getSchedule().overlaps(schedule)) {
                return true;
            }
        }
        return false;
    }

    private List<SciencePlan> loadAll() {
        List<SciencePlan> plans = new ArrayList<>();
        for (String key : store.keys()) {
            plans.add(toPlan(store.load(key)));
        }
        return List.copyOf(plans);
    }

    private SciencePlan toPlan(Properties values) {
        TelescopeSite site = TelescopeSite.valueOf(values.getProperty("telescope"));
        PlanSchedule schedule = new PlanSchedule(
                LocalDate.parse(values.getProperty("startDate")),
                LocalDate.parse(values.getProperty("endDate")), site);
        StarSystemInfo target = new StarSystemInfo(
                values.getProperty("targetConstellation"),
                Quadrant.valueOf(values.getProperty("targetQuadrant")),
                Month.valueOf(values.getProperty("targetMonth")));
        ColorType colorType = ColorType.valueOf(values.getProperty("colorType"));
        ColorAdjustments color = colorType == ColorType.COLOR
                ? new ColorAdjustments(
                        Double.parseDouble(values.getProperty("brightness")),
                        Double.parseDouble(values.getProperty("saturation")),
                        Double.parseDouble(values.getProperty("luminance")),
                        Double.parseDouble(values.getProperty("hue")))
                : null;
        ToneAdjustments tone = colorType == ColorType.COLOR
                ? null
                : new ToneAdjustments(
                        Double.parseDouble(values.getProperty("highlights")),
                        Double.parseDouble(values.getProperty("shadows")),
                        Double.parseDouble(values.getProperty("whites")),
                        Double.parseDouble(values.getProperty("blacks")));
        DataProcessingSettings settings = new DataProcessingSettings(
                FileType.valueOf(values.getProperty("fileType")),
                FileQuality.valueOf(values.getProperty("fileQuality")),
                colorType, Double.parseDouble(values.getProperty("contrast")),
                Double.parseDouble(values.getProperty("exposure")), color, tone);
        SciencePlanDraft draft = new SciencePlanDraft(
                values.getProperty("planName"), values.getProperty("creator"),
                new BigDecimal(values.getProperty("funding")),
                values.getProperty("objective"), schedule,
                target.getConstellation(), settings);
        SciencePlan plan = new SciencePlan(
                Integer.parseInt(values.getProperty("planId")), draft, target);
        String validatedBy = values.getProperty("validatedBy");
        String validatedAt = values.getProperty("validatedAt");
        plan.restoreState(PlanStatus.valueOf(values.getProperty("status")),
                validatedBy == null || validatedBy.isEmpty() ? null : validatedBy,
                validatedAt == null || validatedAt.isEmpty()
                        ? null : LocalDateTime.parse(validatedAt));
        return plan;
    }

    private Properties toProperties(SciencePlan plan) {
        Properties values = new Properties();
        values.setProperty("planId", String.valueOf(plan.getPlanId()));
        values.setProperty("planName", plan.getPlanName());
        values.setProperty("creator", plan.getCreator());
        values.setProperty("funding", plan.getFunding().toPlainString());
        values.setProperty("objective", plan.getObjective());
        values.setProperty("startDate", plan.getSchedule().getStartDate().toString());
        values.setProperty("endDate", plan.getSchedule().getEndDate().toString());
        values.setProperty("telescope", plan.getSchedule().getTelescope().name());
        values.setProperty("targetConstellation", plan.getTarget().getConstellation());
        values.setProperty("targetQuadrant", plan.getTarget().getQuadrant().name());
        values.setProperty("targetMonth", plan.getTarget().getAvailableMonth().name());
        DataProcessingSettings settings = plan.getDataProcessingSettings();
        values.setProperty("fileType", settings.getFileType().name());
        values.setProperty("fileQuality", settings.getFileQuality().name());
        values.setProperty("colorType", settings.getColorType().name());
        values.setProperty("contrast", String.valueOf(settings.getContrast()));
        values.setProperty("exposure", String.valueOf(settings.getExposure()));
        ColorAdjustments color = settings.getColorAdjustments();
        if (color != null) {
            values.setProperty("brightness", String.valueOf(color.getBrightness()));
            values.setProperty("saturation", String.valueOf(color.getSaturation()));
            values.setProperty("luminance", String.valueOf(color.getLuminance()));
            values.setProperty("hue", String.valueOf(color.getHue()));
        }
        ToneAdjustments tone = settings.getToneAdjustments();
        if (tone != null) {
            values.setProperty("highlights", String.valueOf(tone.getHighlights()));
            values.setProperty("shadows", String.valueOf(tone.getShadows()));
            values.setProperty("whites", String.valueOf(tone.getWhites()));
            values.setProperty("blacks", String.valueOf(tone.getBlacks()));
        }
        values.setProperty("status", plan.getStatus().name());
        values.setProperty("validatedBy",
                plan.getValidatedBy() == null ? "" : plan.getValidatedBy());
        values.setProperty("validatedAt",
                plan.getValidatedAt() == null ? "" : plan.getValidatedAt().toString());
        return values;
    }
}
