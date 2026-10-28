package art.pilipala.worker.job.infra;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.worker.job.domain.PollingTask;

/**
 * provider_task 表的写入/读取契约（worker 侧）。实现在 {@link MybatisProviderTaskRepository}。
 *
 * <p>只有 worker 写 provider_task；api 与 C 端只读 job / job_step。
 */
public interface ProviderTaskRepository {

    /**
     * 写一行上游留痕，返回它的 id（随后要关联到 job_step）。
     */
    UUID insert(UUID jobId, UUID jobStepId, UUID targetId, String upstreamRequestId, String rawStatus, String rawBodyJson);

    /** submit 成功后写 polling 行，返回 provider_task id。 */
    UUID insertPolling(UUID jobId, UUID jobStepId, UUID targetId, String upstreamTaskId,
            String rawStatus, String rawBodyJson, Instant nextPollAt);

    /** 当前 job 最近的 submitted/polling 行；没有则空。 */
    Optional<PollingTask> findOpenByJob(UUID jobId);

    /** 查询未终态：保持 polling，更新 poll_count / next_poll_at / 原文。 */
    boolean reschedule(UUID providerTaskId, String rawStatus, String rawBodyJson, Instant nextPollAt, int pollCount);

    /** 查询终态：succeeded / failed / cancelled，并写 error_class / latency。 */
    boolean finish(UUID providerTaskId, String status, String rawStatus, String rawBodyJson,
            String errorClass, Integer latencyMs);
}
