package art.pilipala.worker.job.infra;

import java.util.UUID;

/**
 * provider_task 表的写入契约（worker 侧）。实现在 {@link MybatisProviderTaskRepository}。
 *
 * <p>只有 worker 写 provider_task；api 与 C 端只读 job / job_step。
 */
public interface ProviderTaskRepository {

    /**
     * 写一行上游留痕，返回它的 id（随后要关联到 job_step）。
     */
    UUID insert(UUID jobId, UUID jobStepId, String upstreamRequestId, String rawStatus, String rawBodyJson);
}
