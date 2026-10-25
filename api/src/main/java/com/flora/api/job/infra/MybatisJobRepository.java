package com.flora.api.job.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.flora.api.job.domain.Job;
import com.flora.api.job.domain.JobStatus;

@Repository
@Primary
public class MybatisJobRepository implements JobRepository {

    private final JobMapper mapper;

    public MybatisJobRepository(JobMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Job> insertIfAbsent(
            UUID userId, String capability, String model, String paramsJson, String idempotencyKey, UUID holdId) {
        return Optional.ofNullable(mapper.insertIfAbsent(userId, capability, model, paramsJson, idempotencyKey, holdId));
    }

    @Override
    public Optional<Job> findById(UUID jobId) {
        return Optional.ofNullable(mapper.findById(jobId));
    }

    @Override
    public Optional<Job> findByIdAndUser(UUID jobId, UUID userId) {
        return Optional.ofNullable(mapper.findByIdAndUser(jobId, userId));
    }

    @Override
    public Optional<Job> claim(UUID jobId) {
        return Optional.ofNullable(mapper.claim(jobId));
    }

    @Override
    public boolean updateStatus(UUID jobId, JobStatus from, JobStatus to, String errorCode) {
        return mapper.updateStatus(jobId, from.value(), to.value(), errorCode) > 0;
    }

    @Override
    public boolean updateResult(UUID jobId, String resultObjectKeysJson) {
        return mapper.updateResult(jobId, resultObjectKeysJson) > 0;
    }

    @Override
    public boolean requestCancel(UUID jobId) {
        return mapper.requestCancel(jobId) > 0;
    }

    @Override
    public List<Job> findByUserOrderByCreatedAtDesc(UUID userId, int limit) {
        return mapper.findByUserOrderByCreatedAtDesc(userId, limit);
    }

    @Override
    public boolean requeueForAdmin(UUID jobId, boolean clearHoldId) {
        return mapper.requeueForAdmin(jobId, clearHoldId) > 0;
    }
}
