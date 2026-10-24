package com.flora.api.job.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code flora.job.*} 的配置。全部来自 application.yml 的环境变量占位。
 *
 * <p>这里的队列名只是「传输实现」的配置，禁止写进 job 行（ADR 0003）。
 *
 * @param transport       rabbit（默认）或 fake
 * @param exchange        交换机名
 * @param routingKey      routing key
 * @param queue           主队列名
 * @param deadLetterQueue 死信队列名
 * @param leaseTtlSeconds Redis 短锁 TTL（秒）
 * @param outbox          outbox 轮询参数
 */
@ConfigurationProperties(prefix = "flora.job")
public record JobTransportProperties(
        String transport,
        String exchange,
        String routingKey,
        String queue,
        String deadLetterQueue,
        long leaseTtlSeconds,
        Outbox outbox) {

    /**
     * @param pollMs     轮询间隔（毫秒）
     * @param batchSize  单轮最多投递多少条
     * @param maxAttempts 同一条消息连续投递失败多少次后停在 failed
     */
    public record Outbox(long pollMs, int batchSize, int maxAttempts) {
    }
}
