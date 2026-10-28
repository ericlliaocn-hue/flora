package art.pilipala.worker.job.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.worker.gateway.AnytokenAttemptResult;
import art.pilipala.worker.job.domain.ClaimedAttempt;
import art.pilipala.worker.job.domain.JobOutcome;
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
 * job 状态机的两段短事务：claim 与 complete。
 *
 * <p>两段之间是上游调用（可能很慢），所以 **绝不在事务里调上游**：
 * claim 提交 → 事务外调 Anytoken → complete 提交。
 *
 * <p>complete 里的「写 dead」必须先提交事务，消费者再决定是否把消息投进死信队列，
 * 否则抛异常会把已经写好的 dead 状态回滚掉。
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
            log.info("job 状态不是 queued（{}），幂等跳过：{}", job.status().value(), jobId);
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
                running.capability(), running.model(), running.productId(), running.targetId(), running.paramsJson()));
    }

    /**
     * complete 短事务：插 provider_task + 收尾 job_step + 推进 job。
     *
     * <p>取消意图优先：运行期间用户点了取消，无论上游这次成功还是失败，
     * job_step 与 job 一律落 cancelled，且不写 result_object_keys。
     */
    @Transactional
    public JobOutcome complete(ClaimedAttempt attempt, AnytokenAttemptResult result) {
        UUID providerTaskId = providerTasks.insert(attempt.jobId(), attempt.jobStepId(), attempt.targetId(),
                result.upstreamRequestId(), result.rawStatus(), result.rawBodyJson());

        WorkerJob job = jobs.findById(attempt.jobId()).orElseThrow(
                () -> new IllegalStateException("job 不存在：" + attempt.jobId()));

        if (job.cancelRequestedAt() != null) {
            steps.end(job.id(), job.attempt(), WorkerStepStatus.CANCELLED, WorkerErrorCodes.JOB_CANCELLED, providerTaskId);
            jobs.updateStatus(job.id(), WorkerJobStatus.RUNNING, WorkerJobStatus.CANCELLED, WorkerErrorCodes.JOB_CANCELLED);
            log.info("运行期间收到取消，收敛为 cancelled：{}", job.id());
            return JobOutcome.CANCELLED;
        }

        if (result.succeeded()) {
            steps.end(job.id(), job.attempt(), WorkerStepStatus.SUCCEEDED, null, providerTaskId);
            jobs.updateResult(job.id(), resultKeysJson(result.outputObjectKeys()));
            jobs.updateStatus(job.id(), WorkerJobStatus.RUNNING, WorkerJobStatus.SUCCEEDED, null);
            return JobOutcome.SUCCEEDED;
        }

        boolean retryable = result.retryable() && job.attempt() < job.maxAttempts();
        if (retryable) {
            steps.end(job.id(), job.attempt(), WorkerStepStatus.FAILED, result.errorCode(), providerTaskId);
            jobs.updateStatus(job.id(), WorkerJobStatus.RUNNING, WorkerJobStatus.FAILED, result.errorCode());
            jobs.updateStatus(job.id(), WorkerJobStatus.FAILED, WorkerJobStatus.QUEUED, result.errorCode());
            UUID eventId = UUID.randomUUID();
            outbox.insertPending(eventId, "job", job.id(), messagePayload(job.id(), eventId));
            return JobOutcome.RETRY_QUEUED;
        }

        steps.end(job.id(), job.attempt(), WorkerStepStatus.FAILED, result.errorCode(), providerTaskId);
        jobs.updateStatus(job.id(), WorkerJobStatus.RUNNING, WorkerJobStatus.FAILED, result.errorCode());
        jobs.updateStatus(job.id(), WorkerJobStatus.FAILED, WorkerJobStatus.DEAD, result.errorCode());
        return JobOutcome.DEAD;
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
