package com.pilipala.worker.job.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code pilipala.job.*} 配置（worker 侧）。
 *
 * <p>队列名只是传输实现，不进 job 表（ADR 0003）。
 */
@ConfigurationProperties(prefix = "pilipala.job")
public record WorkerJobProperties(
        String exchange,
        String routingKey,
        String queue,
        String deadLetterQueue,
        long leaseTtlSeconds) {
}
