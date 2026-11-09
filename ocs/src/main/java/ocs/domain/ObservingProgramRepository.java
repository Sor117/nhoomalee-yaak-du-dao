package ocs.domain;

import java.util.Optional;

public interface ObservingProgramRepository {
    public int nextId();
    public ObservingProgram save(ObservingProgram program);
    public Optional<ObservingProgram> findById(int programId);
}
