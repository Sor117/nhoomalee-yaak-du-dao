package ocs.domain;

import java.util.List;

public final class ObservingProgramDraft {
    private final OpticsConfiguration optics;
    private final MechanismConfiguration mechanism;
    private final List<TelePositionPair> telePositionPairs;

    public ObservingProgramDraft(OpticsConfiguration optics, MechanismConfiguration mechanism, List<TelePositionPair> telePositionPairs) {
        this.optics = optics;
        this.mechanism = mechanism;
        this.telePositionPairs = telePositionPairs;
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
}
