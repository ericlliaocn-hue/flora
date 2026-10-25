package art.pilipala.api.job.application;

import java.util.UUID;

import art.pilipala.api.common.ErrorCodes;
import art.pilipala.api.job.domain.Job;
import art.pilipala.api.job.domain.JobStatus;
import art.pilipala.api.job.infra.JobException;
import art.pilipala.api.job.infra.JobLease;
import art.pilipala.api.job.infra.JobRepository;
import art.pilipala.api.job.infra.JobStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 领取一个排队中的任务。
 *
 * <p>权威在 Postgres：queued → running 且 attempt+1，同时插一行 job_step(running)；
 * Redis 短锁 {@code job:lease:{job_id}} 只是防重复执行，拿不到锁也以 DB 状态为准。
 *
 * <p>WP4 不让 worker 进程消费，WP5 的消费者调的就是这个用例（同一份合同）。
 */
@Service
public class ClaimJob {

    private static final Logger log = LoggerFactory.getLogger(ClaimJob.class);

    private final JobRepository jobs;
    private final JobStepRepository steps;
    private final JobLease lease;
    private final HoldSettlement holds;

    public ClaimJob(JobRepository jobs, JobStepRepository steps, JobLease lease, HoldSettlement holds) {
        this.jobs = jobs;
        this.steps = steps;
        this.lease = lease;
        this.holds = holds;
    }

    @Transactional
    public ClaimResult claim(UUID jobId, String workerId) {
        Job current = jobs.findById(jobId).orElseThrow(JobException::notFound);
        if (current.status() == JobStatus.QUEUED && current.cancelRequestedAt() != null) {
            jobs.updateStatus(jobId, JobStatus.QUEUED, JobStatus.CANCELLED, ErrorCodes.JOB_CANCELLED);
            holds.settle(current.holdId(), JobStatus.CANCELLED);
            return new ClaimResult(jobId.toString(), JobStatus.CANCELLED.value(), false);
        }

        var claimed = jobs.claim(jobId);
        if (claimed.isEmpty()) {
            Job latest = jobs.findById(jobId).orElseThrow(JobException::notFound);
            return new ClaimResult(jobId.toString(), latest.status().value(), false);
        }

        Job running = claimed.get();
        steps.start(running.id(), running.attempt());
        if (!lease.acquire(running.id(), workerId)) {
            log.warn("Redis lease 已被占用，DB 状态为准 job={} worker={}", running.id(), workerId);
        }
        return new ClaimResult(jobId.toString(), JobStatus.RUNNING.value(), true);
    }
}
