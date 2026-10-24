package com.flora.worker.job.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flora.worker.gateway.AnytokenAttemptResult;
import com.flora.worker.gateway.AnytokenGateway;
import com.flora.worker.gateway.AnytokenGatewayAdapter;
import com.flora.worker.gateway.AnytokenProperties;
import com.flora.worker.gateway.AnytokenRequest;
import com.flora.worker.job.domain.JobOutcome;
import com.flora.worker.job.domain.WorkerJobStatus;
import com.flora.worker.job.domain.WorkerStepStatus;
import com.flora.worker.job.support.FakeWorkerJobLease;
import com.flora.worker.job.support.InMemoryWorkerStore;
import org.junit.jupiter.api.Test;

/**
 * worker 的状态机闭环：成功、可重试回 queued、取消、dead。
 *
 * <p>用内存仓储 + 假锁 + 真实 stub 适配器跑，不依赖 Postgres / Redis / RabbitMQ。
 */
class ExecuteJobTest {

    private final InMemoryWorkerStore store = new InMemoryWorkerStore();
    private final FakeWorkerJobLease lease = new FakeWorkerJobLease();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AnytokenGatewayAdapter stubGateway = new AnytokenGatewayAdapter(
            new AnytokenProperties("stub", "http://127.0.0.1:18080", "", 30000, "/v1/generations"), mapper);
    private final WorkerJobLifecycleService lifecycle = new WorkerJobLifecycleService(store, store, store, store, mapper);
    private final ExecuteJob executeJob = new ExecuteJob(lifecycle, stubGateway, lease);

    @Test
    void successfulAttemptEndsSucceeded() {
        UUID jobId = UUID.randomUUID();
        store.enqueue(jobId, "{}");

        assertThat(executeJob.execute(jobId)).isEqualTo(JobOutcome.SUCCEEDED);

        assertThat(store.job(jobId).status()).isEqualTo(WorkerJobStatus.SUCCEEDED);
        assertThat(store.job(jobId).attempt()).isEqualTo(1);
        assertThat(store.resultKeys(jobId)).contains("anytoken/img/" + jobId + "/1.png");
        assertThat(store.allSteps()).hasSize(1);
        assertThat(store.allSteps().get(0).status()).isEqualTo(WorkerStepStatus.SUCCEEDED);
        assertThat(store.allProviderTasks()).hasSize(1);
        assertThat(store.allProviderTasks().get(0).rawStatus()).isEqualTo("200");
        assertThat(store.allOutbox()).isEmpty();
        assertThat(lease.heldCount()).isZero();
    }

    @Test
    void retryableFailureGoesBackToQueuedAndWritesOutbox() {
        UUID jobId = UUID.randomUUID();
        store.enqueue(jobId, "{\"stub_outcome\":\"provider_unavailable\"}");

        assertThat(executeJob.execute(jobId)).isEqualTo(JobOutcome.RETRY_QUEUED);

        assertThat(store.job(jobId).status()).isEqualTo(WorkerJobStatus.QUEUED);
        assertThat(store.job(jobId).attempt()).isEqualTo(1);
        assertThat(store.job(jobId).errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(store.allSteps()).hasSize(1);
        assertThat(store.allSteps().get(0).status()).isEqualTo(WorkerStepStatus.FAILED);
        assertThat(store.allProviderTasks()).hasSize(1);
        assertThat(store.allProviderTasks().get(0).rawStatus()).isEqualTo("503");
        assertThat(store.allOutbox()).hasSize(1);
        assertThat(store.allOutbox().get(0).payloadJson()).contains(jobId.toString());
    }

    @Test
    void attemptsExhaustedBecomeDead() {
        UUID jobId = UUID.randomUUID();
        store.enqueue(jobId, "{\"stub_outcome\":\"provider_unavailable\"}");

        assertThat(executeJob.execute(jobId)).isEqualTo(JobOutcome.RETRY_QUEUED);
        assertThat(executeJob.execute(jobId)).isEqualTo(JobOutcome.RETRY_QUEUED);
        assertThat(executeJob.execute(jobId)).isEqualTo(JobOutcome.DEAD);

        assertThat(store.job(jobId).status()).isEqualTo(WorkerJobStatus.DEAD);
        assertThat(store.job(jobId).attempt()).isEqualTo(3);
        assertThat(store.job(jobId).errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(store.allSteps()).hasSize(3);
        assertThat(store.allProviderTasks()).hasSize(3);
        assertThat(store.allOutbox()).hasSize(2);
    }

    @Test
    void nonRetryableFailureDiesImmediately() {
        UUID jobId = UUID.randomUUID();
        store.enqueue(jobId, "{\"stub_outcome\":\"job_failed\"}");

        assertThat(executeJob.execute(jobId)).isEqualTo(JobOutcome.DEAD);

        assertThat(store.job(jobId).status()).isEqualTo(WorkerJobStatus.DEAD);
        assertThat(store.job(jobId).attempt()).isEqualTo(1);
        assertThat(store.job(jobId).errorCode()).isEqualTo("JOB_FAILED");
        assertThat(store.allSteps()).hasSize(1);
        assertThat(store.allProviderTasks()).hasSize(1);
        assertThat(store.allOutbox()).isEmpty();
    }

    /** 取消意图优先于本次 attempt 的成功结果。 */
    @Test
    void cancelDuringRunOverridesASuccessfulAttempt() {
        UUID jobId = UUID.randomUUID();
        store.enqueue(jobId, "{}");
        AnytokenGateway cancelThenSucceed = request -> {
            store.requestCancel(request.jobId());
            return AnytokenAttemptResult.success(List.of("anytoken/img/x.png"), "req-1", "200", "{}");
        };
        ExecuteJob job = new ExecuteJob(lifecycle, cancelThenSucceed, lease);

        assertThat(job.execute(jobId)).isEqualTo(JobOutcome.CANCELLED);

        assertThat(store.job(jobId).status()).isEqualTo(WorkerJobStatus.CANCELLED);
        assertThat(store.job(jobId).errorCode()).isEqualTo("JOB_CANCELLED");
        assertThat(store.allSteps().get(0).status()).isEqualTo(WorkerStepStatus.CANCELLED);
        assertThat(store.resultKeys(jobId)).isNull();
    }

    @Test
    void queuedJobWithCancelIntentIsCancelledWithoutCallingTheGateway() {
        UUID jobId = UUID.randomUUID();
        store.enqueueWithCancelIntent(jobId, "{}");
        AtomicInteger calls = new AtomicInteger();
        AnytokenGateway counting = request -> {
            calls.incrementAndGet();
            return AnytokenAttemptResult.success(List.of(), "req-1", "200", "{}");
        };
        ExecuteJob job = new ExecuteJob(lifecycle, counting, lease);

        assertThat(job.execute(jobId)).isEqualTo(JobOutcome.SKIPPED);

        assertThat(store.job(jobId).status()).isEqualTo(WorkerJobStatus.CANCELLED);
        assertThat(calls).hasValue(0);
        assertThat(store.allSteps()).isEmpty();
    }

    @Test
    void leaseHeldByAnotherWorkerSkipsTheJob() {
        UUID jobId = UUID.randomUUID();
        store.enqueue(jobId, "{}");
        lease.lockByOtherWorker();

        assertThat(executeJob.execute(jobId)).isEqualTo(JobOutcome.SKIPPED);

        assertThat(store.job(jobId).status()).isEqualTo(WorkerJobStatus.QUEUED);
        assertThat(store.allSteps()).isEmpty();
        assertThat(store.allProviderTasks()).isEmpty();
    }

    @Test
    void unknownJobIsSkipped() {
        assertThat(executeJob.execute(UUID.randomUUID())).isEqualTo(JobOutcome.SKIPPED);
    }
}
