package ocs.domain;

import java.util.List;
import java.util.Optional;

public interface SciencePlanRepository {
    public int nextId();
    public SciencePlan save(SciencePlan plan);
    public Optional<SciencePlan> findById(int planId);
    public List<SciencePlan> findByCreator(String creator);
    public List<SciencePlan> findByStatus(PlanStatus status);
    public boolean existsByName(String planName);
    public boolean existsOverlapping(PlanSchedule schedule);
}
