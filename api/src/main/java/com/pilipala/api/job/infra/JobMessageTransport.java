package com.pilipala.api.job.infra;

import com.pilipala.api.job.domain.OutboxMessage;

/**
 * 消息投递契约（队列无关）。
 *
 * <p>实现：{@link RabbitJobTransport}（真 broker）与 {@link FakeJobTransport}（测试 / 本地演练）。
 * 换 Transport 不改状态枚举，也不改 job 表（ADR 0003）。
 */
public interface JobMessageTransport {

    /**
     * 投递一条 job 消息。消息体只有 {@code job_id} 与传输层 {@code event_id}。
     *
     * @throws RuntimeException 投递失败时抛出，由调用方决定重试策略
     */
    void publish(OutboxMessage message);
}
