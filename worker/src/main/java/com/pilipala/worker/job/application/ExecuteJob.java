package com.pilipala.worker.job.application;

import java.util.Optional;
import java.util.UUID;

import com.pilipala.worker.gateway.AnytokenGateway;
import com.pilipala.worker.gateway.AnytokenRequest;
import com.pilipala.worker.gateway.AnytokenAttemptResult;
import com.pilipala.worker.job.domain.ClaimedAttempt;
import com.pilipala.worker.job.domain.JobOutcome;
import com.pilipala.worker.job.infra.WorkerJobLease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 跑完一条 job：抢锁 → claim 短事务 → 事务外调上游 → complete 短事务 → 释放锁。
 *
 * <p>本类 **不** 加 {@code @Transactional}：claim 与 complete 各自是独立短事务，
 * 中间的上游调用不在任何数据库事务里。
 */
@Service
public class ExecuteJob {

    private static final Logger log = LoggerFactory.getLogger(ExecuteJob.class);

    private final WorkerJobLifecycleService lifecycle;
    private final AnytokenGateway gateway;
    private final WorkerJobLease lease;
    private final String workerId;

    public ExecuteJob(WorkerJobLifecycleService lifecycle, AnytokenGateway gateway, WorkerJobLease lease) {
        this.lifecycle = lifecycle;
        this.gateway = gateway;
        this.lease = lease;
        this.workerId = "worker-" + UUID.randomUUID();
    }

    public JobOutcome execute(UUID jobId) {
        if (!lease.acquire(jobId, workerId)) {
            log.info("job 被其它 worker 持锁，跳过：{}", jobId);
            return JobOutcome.SKIPPED;
        }
        try {
            Optional<ClaimedAttempt> claimed = lifecycle.claim(jobId);
            if (claimed.isEmpty()) {
                return JobOutcome.SKIPPED;
            }
            ClaimedAttempt attempt = claimed.get();
            AnytokenAttemptResult result = gateway.execute(new AnytokenRequest(
                    attempt.jobId(), attempt.attempt(), attempt.capability(), attempt.model(), attempt.paramsJson()));
            return lifecycle.complete(attempt, result);
        } finally {
            lease.release(jobId, workerId);
        }
    }
}
