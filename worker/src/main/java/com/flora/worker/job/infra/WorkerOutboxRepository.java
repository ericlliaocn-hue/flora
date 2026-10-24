package com.flora.worker.job.infra;

import java.util.UUID;

/**
 * outbox_message 的写入契约（worker 侧）。实现在 {@link JdbcWorkerOutboxRepository}。
 *
 * <p>worker 只写 pending：重试靠 outbox 机制再次投递，不在 worker 线程里 sleep 重试。
 */
public interface WorkerOutboxRepository {

    /** 写一条 pending 消息。id 由调用方生成（消息体里的 event_id 就是它）。 */
    void insertPending(UUID id, String aggregateType, UUID aggregateId, String payloadJson);
}
