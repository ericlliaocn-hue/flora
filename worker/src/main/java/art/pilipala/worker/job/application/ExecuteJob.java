package art.pilipala.worker.job.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.worker.gateway.AnytokenGateway;
import art.pilipala.worker.gateway.AnytokenRequest;
import art.pilipala.worker.gateway.AnytokenAttemptResult;
import art.pilipala.worker.gateway.newapi.NewApiSubmitResult;
import art.pilipala.worker.gateway.newapi.NewApiTaskStatus;
import art.pilipala.worker.job.domain.ClaimedAttempt;
import art.pilipala.worker.job.domain.JobOutcome;
import art.pilipala.worker.job.domain.PollingAttempt;
import art.pilipala.worker.job.domain.WorkerErrorCodes;
import art.pilipala.worker.job.infra.WorkerJobLease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 跑完一条 job：
 *
 * <ul>
 *   <li>queued：claim → submit（Anytoken 存量或 NewAPI target）；NewAPI 成功只写 polling，立即返回；</li>
 *   <li>running + 打开的 provider_task：resume → query；成功收尾，未终态重排下一次查询。</li>
 * </ul>
 *
 * <p>本类 **不** 加 {@code @Transactional}：claim/startPolling/reschedule/complete 各自是短事务，
 * 中间的上游调用不在任何数据库事务里；查询阶段 worker 不长时间持有租约。
 */
@Service
public class ExecuteJob {

    private static final Logger log = LoggerFactory.getLogger(ExecuteJob.class);

    private final WorkerJobLifecycleService lifecycle;
    private final AnytokenGateway gateway;
    private final WorkerJobLease lease;
    private final VideoTaskGateway videoGateway;
    private final long pollIntervalMs;
    private final String workerId;

    public ExecuteJob(WorkerJobLifecycleService lifecycle, AnytokenGateway gateway, WorkerJobLease lease,
            VideoTaskGateway videoGateway,
            @Value("${pilipala.gateway.newapi.poll-interval-ms:2000}") long pollIntervalMs) {
        this.lifecycle = lifecycle;
        this.gateway = gateway;
        this.lease = lease;
        this.videoGateway = videoGateway;
        this.pollIntervalMs = Math.max(1, pollIntervalMs);
        this.workerId = "worker-" + UUID.randomUUID();
    }

    public JobOutcome execute(UUID jobId) {
        if (!lease.acquire(jobId, workerId)) {
            log.info("job 被其它 worker 持锁，跳过：{}", jobId);
            return JobOutcome.SKIPPED;
        }
        try {
            Optional<ClaimedAttempt> claimed = lifecycle.claim(jobId);
            if (claimed.isPresent()) {
                ClaimedAttempt attempt = claimed.get();
                if (attempt.gatewayModel() == null || attempt.gatewayModel().isBlank()) {
                    AnytokenAttemptResult result = gateway.execute(new AnytokenRequest(attempt.jobId(),
                            attempt.attempt(), attempt.capability(), attempt.model(), attempt.paramsJson()));
                    return lifecycle.complete(attempt, result);
                }
                return submitVideo(attempt);
            }
            Optional<PollingAttempt> polling = lifecycle.resume(jobId);
            if (polling.isPresent()) {
                return queryVideo(polling.get());
            }
            return JobOutcome.SKIPPED;
        } finally {
            lease.release(jobId, workerId);
        }
    }

    private JobOutcome submitVideo(ClaimedAttempt attempt) {
        NewApiSubmitResult submitted = videoGateway.submit(attempt);
        if (!submitted.accepted()) {
            return lifecycle.complete(attempt, failure(submitted.errorCode(), submitted.retryable(),
                    submitted.rawStatus(), submitted.rawBody()));
        }
        try {
            lifecycle.startPolling(attempt, submitted.taskId(), submitted.rawStatus(), submitted.rawBody(),
                    Instant.now().plusMillis(pollIntervalMs));
            return JobOutcome.POLLING;
        } catch (RuntimeException e) {
            log.error("failed to schedule video poll, jobId={}", attempt.jobId(), e);
            return lifecycle.complete(attempt, failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true,
                    "POLL_START", "failed to schedule poll"));
        }
    }

    private JobOutcome queryVideo(PollingAttempt polling) {
        NewApiTaskStatus status = videoGateway.query(polling);
        if (status.succeeded()) {
            AnytokenAttemptResult stored = videoGateway.store(polling, status);
            if (stored.succeeded()) {
                return lifecycle.completePolling(polling, stored);
            }
            return reschedule(polling, status);
        }
        if (status.terminal()) {
            return lifecycle.completePolling(polling, failure(status.errorCode(), status.retryable(),
                    status.rawStatus(), status.rawBody()));
        }
        return reschedule(polling, status);
    }

    private JobOutcome reschedule(PollingAttempt polling, NewApiTaskStatus status) {
        lifecycle.reschedulePolling(polling, status.rawStatus(), status.rawBody(),
                Instant.now().plusMillis(pollIntervalMs));
        return JobOutcome.POLLING;
    }

    private static AnytokenAttemptResult failure(String errorCode, boolean retryable, String rawStatus,
            String rawBody) {
        return AnytokenAttemptResult.failure(
                errorCode == null ? WorkerErrorCodes.PROVIDER_UNAVAILABLE : errorCode,
                retryable, null, rawStatus, rawBody);
    }
}
