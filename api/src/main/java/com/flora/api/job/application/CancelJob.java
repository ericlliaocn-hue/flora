package com.flora.api.job.application;

import java.util.UUID;

import com.flora.api.common.ErrorCodes;
import com.flora.api.job.domain.Job;
import com.flora.api.job.domain.JobStatus;
import com.flora.api.job.domain.JobTransitions;
import com.flora.api.job.infra.JobException;
import com.flora.api.job.infra.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 取消任务。
 *
 * <p>非终态：先写 {@code cancel_requested_at}；
 * 仍处于 created / queued → 直接 cancelled；
 * 已在 running → 保留意图，等 Claim 或驱动下一步时收尾成 cancelled。
 * 已终态 → 幂等返回当前状态。
 */
@Service
public class CancelJob {

    private final JobRepository jobs;

    public CancelJob(JobRepository jobs) {
        this.jobs = jobs;
    }

    @Transactional
    public JobView cancel(UUID userId, UUID jobId, String locale) {
        Job job = jobs.findByIdAndUser(jobId, userId).orElseThrow(JobException::notFound);
        if (job.status().isTerminal()) {
            return JobView.of(job, locale);
        }

        jobs.requestCancel(jobId);
        if (job.status() == JobStatus.QUEUED || job.status() == JobStatus.CREATED) {
            JobTransitions.requireCancellable(job.status());
            jobs.updateStatus(jobId, job.status(), JobStatus.CANCELLED, ErrorCodes.JOB_CANCELLED);
            Job cancelled = jobs.findById(jobId).orElseThrow(JobException::notFound);
            return JobView.of(cancelled, locale);
        }
        // running / failed：保留取消意图，等驱动下一步时收尾。
        return JobView.of(job, locale);
    }
}
