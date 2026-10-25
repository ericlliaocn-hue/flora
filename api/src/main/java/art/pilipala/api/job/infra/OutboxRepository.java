package art.pilipala.api.job.infra;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.job.domain.OutboxMessage;

/**
 * outbox_message 表的存取契约。实现在 {@link MybatisOutboxRepository}。
 *
 * <p>outbox 只负责「事务内写、事务外投递」；投递语义由
 * {@code JobMessageTransport} 与 dispatcher 决定。
 */
public interface OutboxRepository {

    /** 写一条 pending 消息。id 由调用方生成（消息体里的 event_id 就是它）。 */
    OutboxMessage insertPending(UUID id, String aggregateType, UUID aggregateId, String payloadJson);

    /** 取一批 pending 消息，按创建时间排序。 */
    List<OutboxMessage> findPending(int limit);

    /** 投递成功 → sent。 */
    boolean markSent(UUID id);

    /** 连续失败到上限 → failed（不再自动重试）。 */
    boolean markFailed(UUID id);
}
