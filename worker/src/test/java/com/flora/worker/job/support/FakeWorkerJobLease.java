package com.flora.worker.job.support;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.flora.worker.job.infra.WorkerJobLease;

/**
 * 内存版 Redis 短锁：worker 状态机测试不需要 Redis。
 */
public class FakeWorkerJobLease implements WorkerJobLease {

    private final Set<String> held = new LinkedHashSet<>();
    private boolean lockedByOtherWorker;

    @Override
    public boolean acquire(UUID jobId, String workerId) {
        if (lockedByOtherWorker) {
            return false;
        }
        return held.add(jobId + "|" + workerId);
    }

    @Override
    public void release(UUID jobId, String workerId) {
        held.remove(jobId + "|" + workerId);
    }

    /** 模拟「锁被别的 worker 持有」。 */
    public void lockByOtherWorker() {
        lockedByOtherWorker = true;
    }

    /** 当前还持有的锁数量。 */
    public int heldCount() {
        return held.size();
    }
}
