package com.pilipala.api.job.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * outbox_message 行：事务内写、事务外投递。
 *
 * <p>投递成功 → status=sent；连续失败到上限 → status=failed（不再自动重试）。
 *
 * @param payloadJson 消息体，至少含 {"job_id":"..."}，可含传输层 event_id
 */
public record OutboxMessage(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String payloadJson,
        String status,
        Instant createdAt,
        Instant sentAt) {
}
