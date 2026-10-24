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
 *
 * <p>任务已是终态时顺手做一次幂等结算兜底：worker 刚写完终态，
 * 客户端立刻查 job 时账本就已经收敛（ADR 0004：结算只在 api 侧做）。
 */
@Service
public class GetJob {

    private final JobRepository jobs;
    private final HoldSettlement holds;

    public GetJob(JobRepository jobs, HoldSettlement holds) {
        this.jobs = jobs;
        this.holds = holds;
    }

    public JobView get(UUID userId, UUID jobId, String locale) {
        Job job = jobs.findByIdAndUser(jobId, userId).orElseThrow(JobException::notFound);
        holds.settle(job.holdId(), job.status());
        return JobView.of(job, locale);
    }
}
