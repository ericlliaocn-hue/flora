package art.pilipala.worker.job.domain;

/**
 * 一次消费的结果。
 *
 * <p>{@link #DEAD} 时消费者要把消息投进死信队列（此时数据库状态已提交）。
 */
public enum JobOutcome {

    /** 成功。 */
    SUCCEEDED,
    /** 可重试：job 回到 queued，并写了一条 outbox。 */
    RETRY_QUEUED,
    /** 用户取消优先于本次 attempt 的结果。 */
    CANCELLED,
    /** 终态失败：消息进死信队列。 */
    DEAD,
    /** 已提交上游，等待下一次 query；不持有租约。 */
    POLLING,
    /** 没轮上：任务不存在、不是 queued、或被别的 worker 持锁。 */
    SKIPPED
}
