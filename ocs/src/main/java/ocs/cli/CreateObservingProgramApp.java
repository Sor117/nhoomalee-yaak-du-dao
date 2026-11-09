package ocs.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import ocs.domain.CalibrationUnit;
import ocs.domain.Direction;
import ocs.domain.FoldMirrorType;
import ocs.domain.LightType;
import ocs.domain.MechanismConfiguration;
import ocs.domain.ObservingProgram;
import ocs.domain.ObservingProgramDraft;
import ocs.domain.OpticsConfiguration;
import ocs.domain.OpticsPrimary;
import ocs.domain.SciencePlan;
import ocs.domain.TelePositionPair;
import ocs.service.CreateObservingProgramService;

public final class CreateObservingProgramApp extends AbstractCliApp {
    private final CreateObservingProgramService service;

    public CreateObservingProgramApp(CreateObservingProgramService service) {
        this.service = service;
    }

    @Override
    protected void execute(ArgumentValues values) {
        List<SciencePlan> validated = service.listValidatedPlans();
        print("Validated science plans: " + validated.size() + " found.");
        for (SciencePlan plan : validated) {
            print("  " + plan.getPlanId() + " | " + plan.getPlanName());
        }
        OpticsConfiguration optics = new OpticsConfiguration(
                OpticsPrimary.valueOf(values.getString("optics").toUpperCase(Locale.ROOT)),
                values.getDouble("fstop"), values.getDouble("rms"));
        MechanismConfiguration mechanism = new MechanismConfiguration(
                values.getDouble("folddegree"),
                FoldMirrorType.valueOf(values.getString("foldtype").toUpperCase(Locale.ROOT)),
                values.getInt("module"),
                CalibrationUnit.valueOf(values.getString("calibration").toUpperCase(Locale.ROOT)),
                LightType.valueOf(values.getString("light").toUpperCase(Locale.ROOT)));
        List<TelePositionPair> pairs = new ArrayList<>();
        for (String token : values.getAll("pair")) {
            String[] parts = token.split(":");
            pairs.add(new TelePositionPair(
                    Direction.valueOf(parts[0].toUpperCase(Locale.ROOT)),
                    Double.parseDouble(parts[1])));
        }
        ObservingProgram program = service.createProgram(values.getInt("plan"),
                values.getString("observer"),
                new ObservingProgramDraft(optics, mechanism, pairs));
        print("Created observing program ID " + program.getProgramId()
                + " at " + program.getGeminiLocation()
                + " for science plan ID " + program.getSciencePlan().getPlanId()
                + " with status " + program.getStatus() + ".");
    }
}
