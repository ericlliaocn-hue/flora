package art.pilipala.worker.job.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.worker.job.domain.WorkerJob;
import art.pilipala.worker.job.domain.WorkerJobStatus;

@Repository
public class MybatisWorkerJobRepository implements WorkerJobRepository {

    private final WorkerJobMapper mapper;

    public MybatisWorkerJobRepository(WorkerJobMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<WorkerJob> findById(UUID jobId) {
        return Optional.ofNullable(mapper.findById(jobId));
    }

    @Override
    public Optional<WorkerJob> claim(UUID jobId) {
        return Optional.ofNullable(mapper.claim(jobId));
    }

    @Override
    public boolean updateStatus(UUID jobId, WorkerJobStatus from, WorkerJobStatus to, String errorCode) {
        return mapper.updateStatus(jobId, from.value(), to.value(), errorCode) > 0;
    }

    @Override
    public boolean updateResult(UUID jobId, String resultObjectKeysJson) {
        return mapper.updateResult(jobId, resultObjectKeysJson) > 0;
    }
}
