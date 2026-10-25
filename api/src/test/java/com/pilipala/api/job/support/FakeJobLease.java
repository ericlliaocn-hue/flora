package com.pilipala.api.job.support;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.pilipala.api.job.infra.JobLease;

/**
 * 内存版 lease：状态机测试不需要 Redis。
 */
public class FakeJobLease implements JobLease {

    private final Set<String> held = new LinkedHashSet<>();

    @Override
    public boolean acquire(UUID jobId, String workerId) {
        return held.add(jobId + "|" + workerId);
    }

    @Override
    public void release(UUID jobId, String workerId) {
        held.remove(jobId + "|" + workerId);
    }

    /** 当前还持有的锁数量。 */
    public int heldCount() {
        return held.size();
    }
}
