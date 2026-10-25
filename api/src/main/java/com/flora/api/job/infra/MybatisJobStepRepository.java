package com.flora.api.job.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.flora.api.job.domain.JobStep;
import com.flora.api.job.domain.JobStepStatus;

@Repository
@Primary
public class MybatisJobStepRepository implements JobStepRepository {

    private final JobStepMapper mapper;

    public MybatisJobStepRepository(JobStepMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public JobStep start(UUID jobId, int attempt) {
        return mapper.start(jobId, attempt);
    }

    @Override
    public void end(UUID jobId, int attempt, JobStepStatus status, String errorCode) {
        mapper.end(jobId, attempt, status.value(), errorCode);
    }

    @Override
    public List<JobStep> findByJobIds(List<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return List.of();
        }
        return mapper.findByJobIds(jobIds);
    }
}
