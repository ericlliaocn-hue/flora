package com.flora.worker.job.infra;

import java.util.Optional;
import java.util.UUID;

import com.flora.worker.job.domain.WorkerJob;
import com.flora.worker.job.domain.WorkerJobStatus;

/**
 * job 表的存取契约（worker 侧）。实现在 {@link MybatisWorkerJobRepository}。
 *
 * <p>定义成接口是为了让状态机测试能用内存实现跑，不用连数据库。
 */
public interface WorkerJobRepository {

    Optional<WorkerJob> findById(UUID jobId);

    /**
     * queued → running 且 attempt+1；只有仍处于 queued 才会成功。
     */
    Optional<WorkerJob> claim(UUID jobId);

    /**
     * 按 from → to 迁移状态；from 不匹配时返回 false。
     */
    boolean updateStatus(UUID jobId, WorkerJobStatus from, WorkerJobStatus to, String errorCode);

    /** 成功时写 result_object_keys。 */
    boolean updateResult(UUID jobId, String resultObjectKeysJson);
}
