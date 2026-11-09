package ocs.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import ocs.domain.CalibrationUnit;
import ocs.domain.Direction;
import ocs.domain.FoldMirrorType;
import ocs.domain.LightType;
import ocs.domain.MechanismConfiguration;
import ocs.domain.ObservingProgram;
import ocs.domain.ObservingProgramDraft;
import ocs.domain.ObservingProgramRepository;
import ocs.domain.ObservingProgramStatus;
import ocs.domain.OcsException;
import ocs.domain.OpticsConfiguration;
import ocs.domain.OpticsPrimary;
import ocs.domain.SciencePlan;
import ocs.domain.SciencePlanRepository;
import ocs.domain.TelePositionPair;

/**
 * File-backed observing program repository.
 */
public final class FileObservingProgramRepository implements ObservingProgramRepository {
    private final PropertyFileStore store;
    private final SciencePlanRepository planRepository;

    public FileObservingProgramRepository(SciencePlanRepository planRepository) {
        this.store = new PropertyFileStore("observing_programs");
        this.planRepository = planRepository;
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
    public ObservingProgram save(ObservingProgram program) {
        store.save(String.valueOf(program.getProgramId()), toProperties(program));
        return program;
    }

    @Override
    public Optional<ObservingProgram> findById(int programId) {
        String key = String.valueOf(programId);
        if (!store.contains(key)) {
            return Optional.empty();
        }
        return Optional.of(toProgram(store.load(key)));
    }

    public List<ObservingProgram> loadAll() {
        List<ObservingProgram> programs = new ArrayList<>();
        for (String key : store.keys()) {
            programs.add(toProgram(store.load(key)));
        }
        return List.copyOf(programs);
    }

    public ObservingProgram toProgram(Properties values) {
        int planId = Integer.parseInt(values.getProperty("sciencePlanId"));
        SciencePlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new OcsException(
                        "Missing science plan " + planId));
        OpticsConfiguration optics = new OpticsConfiguration(
                OpticsPrimary.valueOf(values.getProperty("opticsPrimary")),
                Double.parseDouble(values.getProperty("fStop")),
                Double.parseDouble(values.getProperty("opticsSecondaryRmsNm")));
        MechanismConfiguration mechanism = new MechanismConfiguration(
                Double.parseDouble(values.getProperty("foldMirrorDegree")),
                FoldMirrorType.valueOf(values.getProperty("foldMirrorType")),
                Integer.parseInt(values.getProperty("moduleContent")),
                CalibrationUnit.valueOf(values.getProperty("calibrationUnit")),
                LightType.valueOf(values.getProperty("lightType")));
        List<TelePositionPair> pairs = new ArrayList<>();
        String encoded = values.getProperty("telePositionPairs");
        if (encoded != null && !encoded.isBlank()) {
            for (String token : encoded.split(";")) {
                String[] parts = token.split(":");
                pairs.add(new TelePositionPair(Direction.valueOf(parts[0]),
                        Double.parseDouble(parts[1])));
            }
        }
        ObservingProgram program = new ObservingProgram(
                Integer.parseInt(values.getProperty("programId")), plan,
                values.getProperty("createdBy"),
                new ObservingProgramDraft(optics, mechanism, pairs));
        program.restoreStatus(ObservingProgramStatus.valueOf(values.getProperty("status")));
        return program;
    }

    public Properties toProperties(ObservingProgram program) {
        Properties values = new Properties();
        values.setProperty("programId", String.valueOf(program.getProgramId()));
        values.setProperty("sciencePlanId",
                String.valueOf(program.getSciencePlan().getPlanId()));
        values.setProperty("createdBy", program.getCreatedBy());
        OpticsConfiguration optics = program.getOptics();
        values.setProperty("opticsPrimary", optics.getOpticsPrimary().name());
        values.setProperty("fStop", String.valueOf(optics.getFStop()));
        values.setProperty("opticsSecondaryRmsNm",
                String.valueOf(optics.getOpticsSecondaryRmsNm()));
        MechanismConfiguration mechanism = program.getMechanism();
        values.setProperty("foldMirrorDegree",
                String.valueOf(mechanism.getFoldMirrorDegree()));
        values.setProperty("foldMirrorType", mechanism.getFoldMirrorType().name());
        values.setProperty("moduleContent",
                String.valueOf(mechanism.getModuleContent()));
        values.setProperty("calibrationUnit",
                mechanism.getCalibrationUnit().name());
        values.setProperty("lightType", mechanism.getLightType().name());
        StringBuilder pairs = new StringBuilder();
        for (TelePositionPair pair : program.getTelePositionPairs()) {
            if (pairs.length() > 0) {
                pairs.append(';');
            }
            pairs.append(pair.getDirection().name()).append(':')
                    .append(pair.getDegree());
        }
        values.setProperty("telePositionPairs", pairs.toString());
        values.setProperty("status", program.getStatus().name());
        return values;
    }
}
