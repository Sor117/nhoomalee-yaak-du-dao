package ocs.cli;

import java.math.BigDecimal;
import java.util.Locale;

import ocs.domain.ColorAdjustments;
import ocs.domain.ColorType;
import ocs.domain.DataProcessingSettings;
import ocs.domain.FileQuality;
import ocs.domain.FileType;
import ocs.domain.PlanSchedule;
import ocs.domain.SciencePlan;
import ocs.domain.SciencePlanDraft;
import ocs.domain.TelescopeSite;
import ocs.domain.ToneAdjustments;
import ocs.service.CreateSciencePlanService;

public final class CreateSciencePlanApp extends AbstractCliApp {
    private final CreateSciencePlanService service;

    public CreateSciencePlanApp(CreateSciencePlanService service) {
        this.service = service;
    }

    @Override
    protected void execute(ArgumentValues values) {
        ColorType colorType = ColorType.valueOf(
                values.getString("colortype").toUpperCase(Locale.ROOT));
        ColorAdjustments color = colorType == ColorType.COLOR
                ? new ColorAdjustments(values.getDouble("brightness"),
                        values.getDouble("saturation"),
                        values.getDouble("luminance"),
                        values.getDouble("hue"))
                : null;
        ToneAdjustments tone = colorType == ColorType.COLOR
                ? null
                : new ToneAdjustments(values.getDouble("highlights"),
                        values.getDouble("shadows"),
                        values.getDouble("whites"),
                        values.getDouble("blacks"));
        DataProcessingSettings settings = new DataProcessingSettings(
                FileType.valueOf(values.getString("filetype").toUpperCase(Locale.ROOT)),
                FileQuality.valueOf(values.getString("filequality").toUpperCase(Locale.ROOT)),
                colorType, values.getDouble("contrast"), values.getDouble("exposure"),
                color, tone);
        PlanSchedule schedule = new PlanSchedule(values.getDate("start"),
                values.getDate("end"),
                TelescopeSite.valueOf("GEMINI_"
                        + values.getString("telescope").toUpperCase(Locale.ROOT)));
        SciencePlanDraft draft = new SciencePlanDraft(values.getString("name"),
                values.getString("creator"),
                new BigDecimal(values.getString("funding")),
                values.getString("objective"), schedule,
                values.getString("target"), settings);
        SciencePlan plan = service.createPlan(draft);
        print("Created science plan ID " + plan.getPlanId() + " for "
                + plan.getTarget().getConstellation() + " ("
                + plan.getTarget().getQuadrant() + ") on "
                + plan.getSchedule().getTelescope() + " with status "
                + plan.getStatus() + ".");
    }
}
