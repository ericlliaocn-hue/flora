package com.flora.worker.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code flora.gateway.*} 配置。
 *
 * @param mode         stub（本地确定性假上游，默认）或 http（真实 Anytoken）
 * @param baseUrl      http 模式的上游根地址
 * @param apiKey       http 模式的 Bearer key；只从环境变量注入，不入库、不进日志
 * @param timeoutMs    http 模式的超时（毫秒）
 * @param generatePath http 模式的生图路径
 */
@ConfigurationProperties(prefix = "flora.gateway")
public record AnytokenProperties(
        String mode,
        String baseUrl,
        String apiKey,
        long timeoutMs,
        String generatePath) {
}
