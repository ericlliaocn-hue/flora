package com.pilipala.worker.job.infra;

import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.pilipala.worker.job.domain.WorkerStepStatus;

@Repository
public class MybatisWorkerJobStepRepository implements WorkerJobStepRepository {

    private final WorkerJobStepMapper mapper;

    public MybatisWorkerJobStepRepository(WorkerJobStepMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UUID start(UUID jobId, int attempt) {
        return mapper.start(jobId, attempt);
    }

    @Override
    public void end(UUID jobId, int attempt, WorkerStepStatus status, String errorCode, UUID providerTaskId) {
        mapper.end(jobId, attempt, status.value(), errorCode, providerTaskId);
    }
}
