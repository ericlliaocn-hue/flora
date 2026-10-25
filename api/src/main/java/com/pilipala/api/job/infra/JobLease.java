package com.pilipala.api.job.infra;

import java.util.UUID;

/**
 * job 的短锁契约。
 *
 * <p>权威状态在 Postgres（queued → running + attempt++）；这里只是防止两个 worker
 * 同时跑同一个 job 的短锁（Redis，键 {@code job:lease:{job_id}}）。
 * 实现见 {@link RedisJobLease}。
 */
public interface JobLease {

    /** 抢锁；成功返回 true，被别人持有返回 false。 */
    boolean acquire(UUID jobId, String workerId);

    /** 释放锁；只有持有者本人能释放。 */
    void release(UUID jobId, String workerId);
}
