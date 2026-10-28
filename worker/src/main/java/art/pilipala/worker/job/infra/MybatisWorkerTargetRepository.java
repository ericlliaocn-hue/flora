package art.pilipala.worker.job.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.worker.job.domain.WorkerTarget;

@Repository
public class MybatisWorkerTargetRepository implements WorkerTargetRepository {

    private final WorkerTargetMapper mapper;

    public MybatisWorkerTargetRepository(WorkerTargetMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<WorkerTarget> findById(UUID targetId) {
        return Optional.ofNullable(mapper.findById(targetId));
    }
}
