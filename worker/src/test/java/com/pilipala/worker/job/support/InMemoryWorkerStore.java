package com.pilipala.worker.job.support;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.pilipala.worker.job.domain.ProviderTaskRecord;
import com.pilipala.worker.job.domain.WorkerJob;
import com.pilipala.worker.job.domain.WorkerJobStatus;
import com.pilipala.worker.job.domain.WorkerStepStatus;
import com.pilipala.worker.job.infra.ProviderTaskRepository;
import com.pilipala.worker.job.infra.WorkerJobRepository;
import com.pilipala.worker.job.infra.WorkerJobStepRepository;
import com.pilipala.worker.job.infra.WorkerOutboxRepository;

/**
 * 内存版仓储：worker 的状态机测试不需要 Postgres / Redis / RabbitMQ。
 *
 * <p>行为对齐 {@code JdbcWorker*}Repository（状态守卫、claim 的原子迁移、唯一约束），
 * 只是把数据放在内存 Map 里。四个仓储接口一起实现，方便用例拿到同一份数据。
 */
public class InMemoryWorkerStore implements WorkerJobRepository, WorkerJobStepRepository, ProviderTaskRepository, WorkerOutboxRepository {

    private final Map<UUID, WorkerJob> jobs = new LinkedHashMap<>();
    private final Map<UUID, ProviderTaskRecord> providerTasks = new LinkedHashMap<>();
    private final Map<UUID, String> resultKeys = new LinkedHashMap<>();
    private final List<Step> steps = new ArrayList<>();
    private final List<OutboxRow> outbox = new ArrayList<>();

    /** 放一个排队中的 job。 */
    public WorkerJob enqueue(UUID jobId, String paramsJson) {
        Instant now = Instant.now();
        WorkerJob job = new WorkerJob(jobId, WorkerJobStatus.QUEUED, "image.generate", "demo", paramsJson,
                0, 3, null, null, now);
        jobs.put(jobId, job);
        return job;
    }

    /** 放一个排队中、且已有取消意图的 job。 */
    public WorkerJob enqueueWithCancelIntent(UUID jobId, String paramsJson) {
        Instant now = Instant.now();
        WorkerJob job = new WorkerJob(jobId, WorkerJobStatus.QUEUED, "image.generate", "demo", paramsJson,
                0, 3, now, null, now);
        jobs.put(jobId, job);
        return job;
    }

    @Override
    public Optional<WorkerJob> findById(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public Optional<WorkerJob> claim(UUID jobId) {
        WorkerJob job = jobs.get(jobId);
        if (job == null || job.status() != WorkerJobStatus.QUEUED) {
            return Optional.empty();
        }
        WorkerJob claimed = copy(job, WorkerJobStatus.RUNNING, job.errorCode(), job.attempt() + 1, job.cancelRequestedAt());
        jobs.put(jobId, claimed);
        return Optional.of(claimed);
    }

    @Override
    public boolean updateStatus(UUID jobId, WorkerJobStatus from, WorkerJobStatus to, String errorCode) {
        WorkerJob job = jobs.get(jobId);
        if (job == null || job.status() != from) {
            return false;
        }
        jobs.put(jobId, copy(job, to, errorCode, job.attempt(), job.cancelRequestedAt()));
        return true;
    }

    @Override
    public boolean updateResult(UUID jobId, String resultObjectKeysJson) {
        WorkerJob job = jobs.get(jobId);
        if (job == null || job.status() != WorkerJobStatus.RUNNING) {
            return false;
        }
        jobs.put(jobId, new WorkerJob(job.id(), job.status(), job.capability(), job.model(), job.paramsJson(),
                job.attempt(), job.maxAttempts(), job.cancelRequestedAt(), job.errorCode(), Instant.now()));
        resultKeys.put(jobId, resultObjectKeysJson);
        return true;
    }

    @Override
    public UUID start(UUID jobId, int attempt) {
        UUID stepId = UUID.randomUUID();
        steps.add(new Step(stepId, jobId, attempt, WorkerStepStatus.RUNNING, null, null));
        return stepId;
    }

    @Override
    public void end(UUID jobId, int attempt, WorkerStepStatus status, String errorCode, UUID providerTaskId) {
        steps.stream()
                .filter(step -> step.jobId().equals(jobId) && step.attempt() == attempt)
                .findFirst()
                .ifPresent(step -> {
                    step.status = status;
                    step.errorCode = errorCode;
                    step.providerTaskId = providerTaskId;
                });
    }

    @Override
    public UUID insert(UUID jobId, UUID jobStepId, String upstreamRequestId, String rawStatus, String rawBodyJson) {
        UUID id = UUID.randomUUID();
        providerTasks.put(id, new ProviderTaskRecord(id, jobId, jobStepId, upstreamRequestId, rawStatus, rawBodyJson, Instant.now()));
        return id;
    }

    @Override
    public void insertPending(UUID id, String aggregateType, UUID aggregateId, String payloadJson) {
        outbox.add(new OutboxRow(id, aggregateType, aggregateId, payloadJson));
    }

    /** 模拟用户点取消：写 cancel_requested_at（job 仍按原状态跑）。 */
    public void requestCancel(UUID jobId) {
        WorkerJob job = jobs.get(jobId);
        if (job == null) {
            return;
        }
        jobs.put(jobId, copy(job, job.status(), job.errorCode(), job.attempt(), Instant.now()));
    }

    /** 当前 job 行。 */
    public WorkerJob job(UUID jobId) {
        return jobs.get(jobId);
    }

    /** 写进 job.result_object_keys 的内容。 */
    public String resultKeys(UUID jobId) {
        return resultKeys.get(jobId);
    }

    /** 所有 step 行。 */
    public List<Step> allSteps() {
        return List.copyOf(steps);
    }

    /** 所有 provider_task 行。 */
    public List<ProviderTaskRecord> allProviderTasks() {
        return List.copyOf(providerTasks.values());
    }

    /** 所有 outbox 行。 */
    public List<OutboxRow> allOutbox() {
        return List.copyOf(outbox);
    }

    private static WorkerJob copy(WorkerJob job, WorkerJobStatus status, String errorCode, int attempt, Instant cancelRequestedAt) {
        return new WorkerJob(job.id(), status, job.capability(), job.model(), job.paramsJson(), attempt,
                job.maxAttempts(), cancelRequestedAt, errorCode, Instant.now());
    }

    /** 内存版 job_step 行。 */
    public static final class Step {
        private final UUID id;
        private final UUID jobId;
        private final int attempt;
        private WorkerStepStatus status;
        private String errorCode;
        private UUID providerTaskId;

        Step(UUID id, UUID jobId, int attempt, WorkerStepStatus status, String errorCode, UUID providerTaskId) {
            this.id = id;
            this.jobId = jobId;
            this.attempt = attempt;
            this.status = status;
            this.errorCode = errorCode;
            this.providerTaskId = providerTaskId;
        }

        public UUID id() {
            return id;
        }

        public UUID jobId() {
            return jobId;
        }

        public int attempt() {
            return attempt;
        }

        public WorkerStepStatus status() {
            return status;
        }

        public String errorCode() {
            return errorCode;
        }

        public UUID providerTaskId() {
            return providerTaskId;
        }
    }

    /** 内存版 outbox 行。 */
    public record OutboxRow(UUID id, String aggregateType, UUID aggregateId, String payloadJson) {
    }
}
