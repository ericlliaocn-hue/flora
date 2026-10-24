package com.flora.api.job.application;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flora.api.common.ErrorCodes;
import com.flora.api.job.domain.Job;
import com.flora.api.job.domain.JobStatus;
import com.flora.api.job.domain.JobStepStatus;
import com.flora.api.job.domain.JobTransitions;
import com.flora.api.job.infra.JobException;
import com.flora.api.job.infra.JobLease;
import com.flora.api.job.infra.JobMessagePayload;
import com.flora.api.job.infra.JobRepository;
import com.flora.api.job.infra.JobStepRepository;
import com.flora.api.job.infra.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 汇报一次 attempt 的结果，收尾状态机。
 *
 * <p>成功：写 result_object_keys → succeeded。
 * 失败：先写 failed（中间可见态），再按 attempt 决定回 queued（重试，另写一条 outbox）
 * 还是 dead（attempt 用尽或不可重试）。
 *
 * <p>本 WP 的选择（brief 的「留给实现者」）：failed 与 queued / dead 在 **同一事务** 里落，
 * 所以 C 端 query 看到的是「已回 queued」而不是中途的 failed；重试中的失败原因保留在
 * {@code job.error_code} 与 job_step 上。
 */
@Service
public class RecordAttemptResult {

    private static final Logger log = LoggerFactory.getLogger(RecordAttemptResult.class);

    private final JobRepository jobs;
    private final JobStepRepository steps;
    private final OutboxRepository outbox;
    private final JobLease lease;
    private final ObjectMapper mapper;
    private final HoldSettlement holds;

    public RecordAttemptResult(JobRepository jobs, JobStepRepository steps, OutboxRepository outbox, JobLease lease,
            ObjectMapper mapper, HoldSettlement holds) {
        this.jobs = jobs;
        this.steps = steps;
        this.outbox = outbox;
        this.lease = lease;
        this.mapper = mapper;
        this.holds = holds;
    }

    @Transactional
    public RecordResult record(UUID jobId, String workerId, AttemptOutcome outcome) {
        Job job = jobs.findById(jobId).orElseThrow(JobException::notFound);
        if (job.status() != JobStatus.RUNNING) {
            log.warn("拒绝非法状态迁移 job={} status={}", jobId, job.status().value());
            throw JobException.validation();
        }

        JobStatus next;
        if (job.cancelRequestedAt() != null) {
            // 取消意图优先：运行期间用户点了取消，即使上游恰好成功也收敛为 cancelled。
            next = cancel(job);
        } else if (outcome.succeeded()) {
            next = succeed(job, outcome);
        } else {
            next = fail(job, outcome);
        }
        lease.release(jobId, workerId);
        return new RecordResult(jobId.toString(), next.value(), job.attempt());
    }

    private JobStatus cancel(Job job) {
        steps.end(job.id(), job.attempt(), JobStepStatus.CANCELLED, ErrorCodes.JOB_CANCELLED);
        JobTransitions.requireCancellable(job.status());
        jobs.updateStatus(job.id(), JobStatus.RUNNING, JobStatus.CANCELLED, ErrorCodes.JOB_CANCELLED);
        holds.settle(job.holdId(), JobStatus.CANCELLED);
        return JobStatus.CANCELLED;
    }

    private JobStatus succeed(Job job, AttemptOutcome outcome) {
        steps.end(job.id(), job.attempt(), JobStepStatus.SUCCEEDED, null);
        jobs.updateResult(job.id(), resultKeysJson(outcome.resultObjectKeys()));
        JobTransitions.requireSucceeded(job.status());
        jobs.updateStatus(job.id(), JobStatus.RUNNING, JobStatus.SUCCEEDED, null);
        holds.settle(job.holdId(), JobStatus.SUCCEEDED);
        return JobStatus.SUCCEEDED;
    }

    private JobStatus fail(Job job, AttemptOutcome outcome) {
        steps.end(job.id(), job.attempt(), JobStepStatus.FAILED, outcome.errorCode());
        JobTransitions.requireFailed(job.status());
        jobs.updateStatus(job.id(), JobStatus.RUNNING, JobStatus.FAILED, outcome.errorCode());

        if (outcome.retryable() && JobTransitions.canRetry(job.attempt(), job.maxAttempts())) {
            JobTransitions.requireRetry(JobStatus.FAILED);
            jobs.updateStatus(job.id(), JobStatus.FAILED, JobStatus.QUEUED, outcome.errorCode());
            UUID eventId = UUID.randomUUID();
            outbox.insertPending(eventId, "job", job.id(), JobMessagePayload.of(job.id(), eventId));
            // 重试不释放也不重冻：冻结单保持 frozen，等最终终态再结算。
            return JobStatus.QUEUED;
        }
        JobTransitions.requireDead(JobStatus.FAILED);
        jobs.updateStatus(job.id(), JobStatus.FAILED, JobStatus.DEAD, outcome.errorCode());
        holds.settle(job.holdId(), JobStatus.DEAD);
        return JobStatus.DEAD;
    }

    private String resultKeysJson(List<String> objectKeys) {
        try {
            return mapper.writeValueAsString(objectKeys);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("结果对象键无法序列化", e);
        }
    }
}
