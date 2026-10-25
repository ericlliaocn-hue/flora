package com.flora.worker.job.infra;

import java.util.UUID;

import org.springframework.stereotype.Repository;

@Repository
public class MybatisProviderTaskRepository implements ProviderTaskRepository {

    private final WorkerProviderTaskMapper mapper;

    public MybatisProviderTaskRepository(WorkerProviderTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UUID insert(UUID jobId, UUID jobStepId, String upstreamRequestId, String rawStatus, String rawBodyJson) {
        return mapper.insert(jobId, jobStepId, upstreamRequestId, rawStatus, rawBodyJson);
    }
}
