package com.flora.api.job.application;

import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.flora.api.job.domain.IdempotencyRecord;
import com.flora.api.job.domain.Job;
import com.flora.api.job.domain.JobStatus;
import com.flora.api.job.domain.JobTransitions;
import com.flora.api.job.infra.IdempotencyRepository;
import com.flora.api.job.infra.JobException;
import com.flora.api.job.infra.JobMessagePayload;
import com.flora.api.job.infra.JobRepository;
import com.flora.api.job.infra.JobRequestHasher;
import com.flora.api.job.infra.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 提交一个托管任务。
 *
 * <p>事务顺序（brief 锁定）：写 job(status=created) → 迁到 queued → 写 outbox(pending)
 * → 写幂等记录；事务提交后由 dispatcher 异步投递。
 *
 * <p>同 key 同 body 重放 → 返回原 job_id，不新建 job、不二次入队；
 * 同 key 不同 body → IDEMPOTENCY_CONFLICT。
 *
 * <p>本 WP 不冻结钱包，所以 {@code hold_id} 恒为 null（钱包整包在 WP6）。
 */
@Service
public class SubmitJob {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final int MAX_CAPABILITY_LENGTH = 64;
    private static final int MAX_MODEL_LENGTH = 128;

    private final JobRepository jobs;
    private final OutboxRepository outbox;
    private final IdempotencyRepository idempotency;
    private final JobRequestHasher hasher;

    public SubmitJob(JobRepository jobs, OutboxRepository outbox, IdempotencyRepository idempotency, JobRequestHasher hasher) {
        this.jobs = jobs;
        this.outbox = outbox;
        this.idempotency = idempotency;
        this.hasher = hasher;
    }

    @Transactional
    public SubmitJobView submit(UUID userId, String idempotencyKey, String capability, String model, JsonNode params, String clientJobId) {
        String key = requireText(idempotencyKey, MAX_IDEMPOTENCY_KEY_LENGTH);
        String capabilityText = requireText(capability, MAX_CAPABILITY_LENGTH);
        String modelText = requireText(model, MAX_MODEL_LENGTH);
        if (params == null || !params.isObject()) {
            throw JobException.validation();
        }
        // client_job_id 只是客户端追踪用的，本 WP 不落库（要落库得先改 data-model）。
        String hash = hasher.hash(capabilityText, modelText, params);

        Optional<IdempotencyRecord> recorded = idempotency.findByUserAndKey(userId, key);
        if (recorded.isPresent()) {
            return replay(recorded.get(), hash);
        }

        Optional<Job> created = jobs.insertIfAbsent(userId, capabilityText, modelText, params.toString(), key);
        if (created.isEmpty()) {
            // 并发同键：另一个事务已经写好 job 与幂等记录，这里只重放它的结果。
            IdempotencyRecord record = idempotency.findByUserAndKey(userId, key).orElseThrow(JobException::idempotencyConflict);
            return replay(record, hash);
        }

        Job job = created.get();
        JobTransitions.requireQueued(job.status());
        if (!jobs.updateStatus(job.id(), JobStatus.CREATED, JobStatus.QUEUED, null)) {
            throw JobException.validation();
        }
        UUID eventId = UUID.randomUUID();
        outbox.insertPending(eventId, "job", job.id(), JobMessagePayload.of(job.id(), eventId));
        idempotency.insert(userId, key, job.id(), hash);
        return new SubmitJobView(job.id().toString(), JobStatus.QUEUED.value(), null);
    }

    private SubmitJobView replay(IdempotencyRecord record, String hash) {
        if (!record.requestHash().equals(hash)) {
            throw JobException.idempotencyConflict();
        }
        Job job = jobs.findById(record.jobId()).orElseThrow(JobException::notFound);
        return new SubmitJobView(job.id().toString(), job.status().value(), job.holdId() == null ? null : job.holdId().toString());
    }

    private static String requireText(String raw, int maxLength) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > maxLength) {
            throw JobException.validation();
        }
        return value;
    }
}
