package art.pilipala.worker.job.infra;

import java.util.UUID;

/**
 * job 的 Redis 短锁（worker 侧）。
 *
 * <p>权威状态在 Postgres；这里只是防同一个 job 被两个 worker 并发跑。
 * 键格式与 api 侧一致：{@code job:lease:{job_id}}。
 */
public interface WorkerJobLease {

    /** 抢锁；成功返回 true，被别人持有返回 false。 */
    boolean acquire(UUID jobId, String workerId);

    /** 释放锁；只有持有者本人能释放。 */
    void release(UUID jobId, String workerId);
}
