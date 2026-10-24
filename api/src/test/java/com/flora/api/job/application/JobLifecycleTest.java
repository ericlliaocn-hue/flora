package com.flora.api.job.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flora.api.job.domain.JobStatus;
import com.flora.api.job.infra.JobException;
import com.flora.api.job.infra.JobMessageTransport;
import com.flora.api.job.infra.FakeJobTransport;
import com.flora.api.job.infra.JobRequestHasher;
import com.flora.api.job.support.FakeJobLease;
import com.flora.api.job.support.InMemoryJobStore;
import org.junit.jupiter.api.Test;

/**
 * Job 域的状态机与幂等：成功、重试回 queued、取消、dead。
 *
 * <p>用内存仓储 + FakeTransport 跑，不依赖 Postgres / Redis / RabbitMQ；
 * 仓储语义与 JDBC 实现一致（见 {@link InMemoryJobStore}）。
 */
class JobLifecycleTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final String WORKER = "worker-1";

    private final InMemoryJobStore store = new InMemoryJobStore();
    private final FakeJobTransport transport = new FakeJobTransport();
    private final FakeJobLease lease = new FakeJobLease();
    private final ObjectMapper mapper = new ObjectMapper();

    private final SubmitJob submitJob = new SubmitJob(store, store, store, new JobRequestHasher());
    private final GetJob getJob = new GetJob(store);
    private final GetJobResult getJobResult = new GetJobResult(store, mapper);
    private final CancelJob cancelJob = new CancelJob(store);
    private final ClaimJob claimJob = new ClaimJob(store, store, lease);
    private final RecordAttemptResult recordAttemptResult = new RecordAttemptResult(store, store, store, lease, mapper);
    private final OutboxDispatchTick dispatchTick = new OutboxDispatchTick(store, transport, 50, 10);

    @Test
    void submitCreatesQueuedJobWithOutboxAndIdempotencyRecord() throws Exception {
        SubmitJobView view = submitJob.submit(USER, "k1", "image.generate", "demo", params("hi"), null);

        assertThat(view.status()).isEqualTo("queued");
        assertThat(view.holdId()).isNull();
        assertThat(store.allJobs()).hasSize(1);
        assertThat(store.allOutbox()).hasSize(1);
        assertThat(store.findByUserAndKey(USER, "k1")).isPresent();

        JsonNode payload = mapper.readTree(store.allOutbox().get(0).payloadJson());
        assertThat(payload.get("job_id").asText()).isEqualTo(view.jobId());
        assertThat(payload.has("event_id")).isTrue();
        assertThat(payload.has("params")).isFalse();
    }

    @Test
    void sameKeySameBodyReplaysTheSameJobWithoutRepublishing() {
        SubmitJobView first = submitJob.submit(USER, "k1", "image.generate", "demo", params("hi"), null);
        SubmitJobView second = submitJob.submit(USER, "k1", "image.generate", "demo", params("hi"), null);

        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(store.allJobs()).hasSize(1);
        assertThat(store.allOutbox()).hasSize(1);
    }

    @Test
    void sameKeyDifferentBodyIsAConflict() {
        submitJob.submit(USER, "k1", "image.generate", "demo", params("hi"), null);

        assertThatThrownBy(() -> submitJob.submit(USER, "k1", "image.generate", "demo", params("别的"), null))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThat(store.allJobs()).hasSize(1);
    }

    @Test
    void otherUsersJobIsNotFound() {
        SubmitJobView view = submitJob.submit(USER, "k1", "image.generate", "demo", params("hi"), null);
        UUID jobId = UUID.fromString(view.jobId());

        assertThatThrownBy(() -> getJob.get(OTHER_USER, jobId, "zh"))
                .isInstanceOf(JobException.class)
                .satisfies(e -> assertThat(((JobException) e).errorCode()).isEqualTo("JOB_NOT_FOUND"));
        assertThatThrownBy(() -> getJobResult.result(OTHER_USER, jobId, "zh"))
                .isInstanceOf(JobException.class);
        assertThatThrownBy(() -> cancelJob.cancel(OTHER_USER, jobId, "zh"))
                .isInstanceOf(JobException.class);
    }

    @Test
    void claimThenSucceedMakesTheResultVisible() {
        UUID jobId = submit("k1");

        ClaimResult claim = claimJob.claim(jobId, WORKER);
        assertThat(claim.claimed()).isTrue();
        assertThat(claim.status()).isEqualTo("running");
        assertThat(lease.heldCount()).isEqualTo(1);

        RecordResult record = recordAttemptResult.record(jobId, WORKER, AttemptOutcome.success(List.of("img/a.png")));
        assertThat(record.status()).isEqualTo("succeeded");

        JobView view = getJob.get(USER, jobId, "zh");
        assertThat(view.status()).isEqualTo("succeeded");
        assertThat(view.attempt()).isEqualTo(1);
        assertThat(view.errorCode()).isNull();

        JobResultView result = getJobResult.result(USER, jobId, "zh");
        assertThat(result.outputs()).hasSize(1);
        assertThat(result.outputs().get(0).objectKey()).isEqualTo("img/a.png");
        assertThat(result.outputs().get(0).url()).isNull();
        assertThat(lease.heldCount()).isZero();
    }

    @Test
    void failedAttemptGoesBackToQueuedAndIsPublishedAgain() {
        UUID jobId = submit("k1");

        claimJob.claim(jobId, WORKER);
        RecordResult record = recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true));

        assertThat(record.status()).isEqualTo("queued");
        JobView view = getJob.get(USER, jobId, "zh");
        assertThat(view.status()).isEqualTo("queued");
        assertThat(view.attempt()).isEqualTo(1);
        assertThat(view.errorCode()).isEqualTo("JOB_FAILED");

        assertThat(store.allOutbox()).hasSize(2);
        assertThat(dispatchTick.tick()).isEqualTo(2);
        assertThat(transport.sent()).hasSize(2);
        assertThat(store.allOutbox()).allMatch(message -> "sent".equals(message.status()));
    }

    @Test
    void attemptsExhaustedBecomeDead() {
        UUID jobId = submit("k1");

        claimJob.claim(jobId, WORKER);
        assertThat(recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true)).status())
                .isEqualTo("queued");
        claimJob.claim(jobId, WORKER);
        assertThat(recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true)).status())
                .isEqualTo("queued");
        claimJob.claim(jobId, WORKER);
        assertThat(recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true)).status())
                .isEqualTo("dead");

        JobView view = getJob.get(USER, jobId, "zh");
        assertThat(view.status()).isEqualTo("dead");
        assertThat(view.attempt()).isEqualTo(3);
        assertThat(view.maxAttempts()).isEqualTo(3);
        assertThat(view.errorCode()).isEqualTo("JOB_FAILED");
    }

    @Test
    void cancellingAQueuedJobIsImmediateAndIdempotent() {
        UUID jobId = submit("k1");

        JobView cancelled = cancelJob.cancel(USER, jobId, "zh");
        assertThat(cancelled.status()).isEqualTo("cancelled");
        assertThat(cancelled.errorCode()).isEqualTo("JOB_CANCELLED");
        assertThat(cancelled.message()).isEqualTo("任务已取消。");

        JobView again = cancelJob.cancel(USER, jobId, "zh");
        assertThat(again.status()).isEqualTo("cancelled");
        assertThat(again.errorCode()).isEqualTo("JOB_CANCELLED");
    }

    @Test
    void cancellingARunningJobKeepsTheIntentUntilTheAttemptReports() {
        UUID jobId = submit("k1");
        claimJob.claim(jobId, WORKER);

        JobView requested = cancelJob.cancel(USER, jobId, "zh");
        assertThat(requested.status()).isEqualTo("running");

        RecordResult record = recordAttemptResult.record(jobId, WORKER, AttemptOutcome.failure("JOB_FAILED", true));
        assertThat(record.status()).isEqualTo("cancelled");
        assertThat(getJob.get(USER, jobId, "zh").errorCode()).isEqualTo("JOB_CANCELLED");
    }

    @Test
    void illegalTransitionIsRejected() {
        UUID jobId = submit("k1");

        assertThatThrownBy(() -> recordAttemptResult.record(jobId, WORKER, AttemptOutcome.success(List.of("img/a.png"))))
                .isInstanceOf(JobException.class);
        assertThat(getJob.get(USER, jobId, "zh").status()).isEqualTo("queued");
    }

    @Test
    void outboxDeliveryFailureKeepsPendingUntilTheLimitIsReached() {
        UUID jobId = submit("k1");
        JobMessageTransport broken = message -> {
            throw new IllegalStateException("broker down");
        };
        OutboxDispatchTick failing = new OutboxDispatchTick(store, broken, 50, 3);

        assertThat(failing.tick()).isZero();
        assertThat(store.allOutbox().get(0).status()).isEqualTo("pending");
        failing.tick();
        assertThat(store.allOutbox().get(0).status()).isEqualTo("pending");
        failing.tick();
        assertThat(store.allOutbox().get(0).status()).isEqualTo("failed");
        assertThat(jobId).isNotNull();
    }

    private UUID submit(String key) {
        return UUID.fromString(submitJob.submit(USER, key, "image.generate", "demo", params("hi"), null).jobId());
    }

    private JsonNode params(String prompt) {
        ObjectNode node = mapper.createObjectNode();
        node.put("prompt", prompt);
        return node;
    }
}
