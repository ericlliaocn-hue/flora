package com.flora.api.job.application;

import java.util.UUID;

import com.flora.api.job.domain.Job;
import com.flora.api.job.infra.JobException;
import com.flora.api.job.infra.JobRepository;
import org.springframework.stereotype.Service;

/**
 * 查询任务状态（给画布轮询用）。不触发生成。
 *
 * <p>他人的 job 与不存在的 job 一样返回 JOB_NOT_FOUND，不泄露存在性。
 */
@Service
public class GetJob {

    private final JobRepository jobs;

    public GetJob(JobRepository jobs) {
        this.jobs = jobs;
    }

    public JobView get(UUID userId, UUID jobId, String locale) {
        Job job = jobs.findByIdAndUser(jobId, userId).orElseThrow(JobException::notFound);
        return JobView.of(job, locale);
    }
}
