package art.pilipala.worker.job.infra;

import java.util.UUID;

import art.pilipala.worker.job.domain.WorkerStepStatus;

/**
 * job_step 表的存取契约（worker 侧）。实现在 {@link MybatisWorkerJobStepRepository}。
 */
public interface WorkerJobStepRepository {

    /** 插入一行 status=running 的 step，返回它的 id。 */
    UUID start(UUID jobId, int attempt);

    /**
     * 收尾：写 step 的终态、错误码、关联的 provider_task 与结束时间。
     */
    void end(UUID jobId, int attempt, WorkerStepStatus status, String errorCode, UUID providerTaskId);
}
