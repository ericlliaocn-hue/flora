package art.pilipala.worker.job.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.worker.gateway.AnytokenAttemptResult;
import art.pilipala.worker.job.domain.ClaimedAttempt;
import art.pilipala.worker.job.domain.JobOutcome;
import art.pilipala.worker.job.domain.PollingAttempt;
import art.pilipala.worker.job.domain.WorkerErrorCodes;
import art.pilipala.worker.job.domain.WorkerJob;
import art.pilipala.worker.job.domain.WorkerJobStatus;
import art.pilipala.worker.job.domain.WorkerStepStatus;
import art.pilipala.worker.job.infra.ProviderTaskRepository;
import art.pilipala.worker.job.infra.WorkerJobRepository;
import art.pilipala.worker.job.infra.WorkerJobStepRepository;
import art.pilipala.worker.job.infra.WorkerOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * job 状态机的短事务：claim（submit 入口）、startPolling、resume + completePolling。
 *
 * <p>submit 成功后 job/step 仍是 running，provider_task 进入 polling；worker 释放租约，
 * 由 api 的 ProviderPollScheduler 按 next_poll_at 重投。查询成功/终态才收尾 job。
 */
@Service
public class WorkerJobLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(WorkerJobLifecycleService.class);

    private final WorkerJobRepository jobs;
    private final WorkerJobStepRepository steps;
    private final ProviderTaskRepository providerTasks;
    private final WorkerOutboxRepository outbox;
    private final ObjectMapper mapper;

    public WorkerJobLifecycleService(WorkerJobRepository jobs, WorkerJobStepRepository steps, ProviderTaskRepository providerTasks, WorkerOutboxRepository outbox, ObjectMapper mapper) {
        this.jobs = jobs;
        this.steps = steps;
        this.providerTasks = providerTasks;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    /**
     * claim 短事务：queued → running + 插 job_step(running)。
     *
     * @return 领到的 attempt；空表示这条消息不用跑（任务不存在、已取消意图、不是 queued、别人抢到了）
     */
    @Transactional
    public Optional<ClaimedAttempt> claim(UUID jobId) {
        WorkerJob job = jobs.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("job 不存在，跳过：{}", jobId);
            return Optional.empty();
        }
        if (job.status() == WorkerJobStatus.QUEUED && job.cancelRequestedAt() != null) {
            log.info("job 已排队但有取消意图，直接取消：{}", jobId);
            jobs.updateStatus(jobId, WorkerJobStatus.QUEUED, WorkerJobStatus.CANCELLED, WorkerErrorCodes.JOB_CANCELLED);
            return Optional.empty();
        }
        if (job.status() != WorkerJobStatus.QUEUED) {
            log.debug("job 状态不是 queued（{}），交给 resume/幂等路径：{}", job.status().value(), jobId);
            return Optional.empty();
        }

        Optional<WorkerJob> claimed = jobs.claim(jobId);
        if (claimed.isEmpty()) {
            log.info("job 已被其它 worker 领走，跳过：{}", jobId);
            return Optional.empty();
        }
        WorkerJob running = claimed.get();
        UUID stepId = steps.start(running.id(), running.attempt(), running.targetId());
        return Optional.of(new ClaimedAttempt(running.id(), stepId, running.attempt(), running.maxAttempts(),
                running.capability(), running.model(), running.productId(), running.targetId(), running.gatewayGroup(),
                running.gatewayModel(), running.gatewayTimeoutSeconds(), running.paramsJson()));
    }

    /** submit 成功：写 polling 行，job/step 保持 running。 */
    @Transactional
    public void startPolling(ClaimedAttempt attempt, String upstreamTaskId, String rawStatus, String rawBodyJson,
            Instant nextPollAt) {
        providerTasks.insertPolling(attempt.jobId(), attempt.jobStepId(), attempt.targetId(), upstreamTaskId,
                rawStatus, rawBodyJson, nextPollAt);
    }

    /** resume：running job + 打开的 provider_task；没有则空。 */
    @Transactional(readOnly = true)
    public Optional<PollingAttempt> resume(UUID jobId) {
        WorkerJob job = jobs.findById(jobId).orElse(null);
        if (job == null || job.status() != WorkerJobStatus.RUNNING) {
            return Optional.empty();
        }
        return providerTasks.findOpenByJob(jobId)
                .map(task -> new PollingAttempt(job.id(), task.jobStepId(), job.attempt(), job.maxAttempts(),
                        job.capability(), job.model(), task.targetId(), task.providerTaskId(), task.upstreamTaskId(),
                        task.pollCount(), job.paramsJson()));
    }

    /** 查询未终态：保持 polling，更新 poll_count / next_poll_at。 */
    @Transactional
    public void reschedulePolling(PollingAttempt attempt, String rawStatus, String rawBodyJson, Instant nextPollAt) {
        providerTasks.reschedule(attempt.providerTaskId(), rawStatus, rawBodyJson, nextPollAt,
                attempt.pollCount() + 1);
    }

    /** 查询终态：更新已有 provider_task，收尾 step/job。 */
    @Transactional
    public JobOutcome completePolling(PollingAttempt attempt, AnytokenAttemptResult result) {
        return finish(attempt.jobId(), attempt.jobStepId(), attempt.attempt(), attempt.maxAttempts(),
                attempt.providerTaskId(), result);
    }

    /**
     * legacy：一次性完成后插 provider_task 再收尾（Anytoken 存量路径）。
     */
    @Transactional
    public JobOutcome complete(ClaimedAttempt attempt, AnytokenAttemptResult result) {
        UUID providerTaskId = providerTasks.insert(attempt.jobId(), attempt.jobStepId(), attempt.targetId(),
                result.upstreamRequestId(), result.rawStatus(), result.rawBodyJson());
        return finish(attempt.jobId(), attempt.jobStepId(), attempt.attempt(), attempt.maxAttempts(),
                providerTaskId, result);
    }

    private JobOutcome finish(UUID jobId, UUID jobStepId, int attempt, int maxAttempts, UUID providerTaskId,
            AnytokenAttemptResult result) {
        WorkerJob job = jobs.findById(jobId).orElseThrow(
                () -> new IllegalStateException("job 不存在：" + jobId));

        if (job.cancelRequestedAt() != null) {
            providerTasks.finish(providerTaskId, "cancelled", result.rawStatus(), result.rawBodyJson(), "cancelled", null);
            steps.end(job.id(), attempt, WorkerStepStatus.CANCELLED, WorkerErrorCodes.JOB_CANCELLED, providerTaskId);
            jobs.updateStatus(job.id(), WorkerJobStatus.RUNNING, WorkerJobStatus.CANCELLED, WorkerErrorCodes.JOB_CANCELLED);
            log.info("运行期间收到取消，收敛为 cancelled：{}", job.id());
            return JobOutcome.CANCELLED;
        }

        if (result.succeeded()) {
            providerTasks.finish(providerTaskId, "succeeded", result.rawStatus(), result.rawBodyJson(), null, null);
            steps.end(job.id(), attempt, WorkerStepStatus.SUCCEEDED, null, providerTaskId);
            jobs.updateResult(job.id(), resultKeysJson(result.outputObjectKeys()));
            jobs.updateStatus(job.id(), WorkerJobStatus.RUNNING, WorkerJobStatus.SUCCEEDED, null);
            return JobOutcome.SUCCEEDED;
        }

        boolean retryable = result.retryable() && attempt < maxAttempts;
        providerTasks.finish(providerTaskId, "failed", result.rawStatus(), result.rawBodyJson(),
                errorClass(result), null);
        if (retryable) {
            steps.end(job.id(), attempt, WorkerStepStatus.FAILED, result.errorCode(), providerTaskId);
            jobs.updateStatus(job.id(), WorkerJobStatus.RUNNING, WorkerJobStatus.FAILED, result.errorCode());
            jobs.updateStatus(job.id(), WorkerJobStatus.FAILED, WorkerJobStatus.QUEUED, result.errorCode());
            UUID eventId = UUID.randomUUID();
            outbox.insertPending(eventId, "job", job.id(), messagePayload(job.id(), eventId));
            return JobOutcome.RETRY_QUEUED;
        }

        steps.end(job.id(), attempt, WorkerStepStatus.FAILED, result.errorCode(), providerTaskId);
        jobs.updateStatus(job.id(), WorkerJobStatus.RUNNING, WorkerJobStatus.FAILED, result.errorCode());
        if (job.productId() != null && job.targetId() != null && fallbackEligible(result.errorCode())) {
            // NewAPI 内部完成 channel fallback；hold 仍冻结。
            return JobOutcome.FAILED;
        }
        jobs.updateStatus(job.id(), WorkerJobStatus.FAILED, WorkerJobStatus.DEAD, result.errorCode());
        return JobOutcome.DEAD;
    }

    private static String errorClass(AnytokenAttemptResult result) {
        String code = result.errorCode();
        if (code == null) {
            return null;
        }
        return switch (code) {
            case WorkerErrorCodes.PROVIDER_UNAVAILABLE -> "transient";
            case WorkerErrorCodes.GATEWAY_TIMEOUT -> "timeout";
            case WorkerErrorCodes.UPSTREAM_VIDEO_FAILED -> "task_failed";
            case WorkerErrorCodes.GATEWAY_INVALID_RESPONSE -> "param";
            default -> "job_failed";
        };
    }

    /** 只有上游任务失败 / 超时 / 网关不可用才值得切下一个 target；参数/协议错误不 fallback。 */
    private static boolean fallbackEligible(String errorCode) {
        return WorkerErrorCodes.UPSTREAM_VIDEO_FAILED.equals(errorCode)
                || WorkerErrorCodes.GATEWAY_TIMEOUT.equals(errorCode)
                || WorkerErrorCodes.PROVIDER_UNAVAILABLE.equals(errorCode);
    }

    /** MQ 消息体：只有 job_id 与传输层 event_id（ADR 0003）。 */
    private static String messagePayload(UUID jobId, UUID eventId) {
        return "{\"job_id\":\"" + jobId + "\",\"event_id\":\"" + eventId + "\"}";
    }

    private String resultKeysJson(List<String> objectKeys) {
        try {
            return mapper.writeValueAsString(objectKeys);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("结果对象键无法序列化", e);
        }
    }
}
