package art.pilipala.worker.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code pilipala.storage.tos.*} 配置。
 *
 * <p>只读，不改。对象存储的唯一选型是火山引擎 TOS（不建多云抽象）。
 *
 * @param mode             stub（本地零密钥，默认）或 http（真实火山 TOS）
 * @param endpoint         TOS 服务端点，例如 {@code https://tos-cn-beijing.volces.com}
 * @param region           区域，例如 {@code cn-beijing}
 * @param bucket           桶名
 * @param accessKey        访问密钥 ID；只从环境变量注入，不入库、不进日志
 * @param secretKey        访问密钥；只从环境变量注入，不入库、不进日志
 * @param presignTtlSeconds 预签名 URL 有效期（秒），范围 60..86400
 * @param timeoutMs        HTTP 超时（毫秒）
 * @param localDir         stub 模式的本地落盘根目录
 */
@ConfigurationProperties(prefix = "pilipala.storage.tos")
public record TosProperties(
        @DefaultValue("stub") String mode,
        @DefaultValue("https://tos-cn-beijing.volces.com") String endpoint,
        @DefaultValue("cn-beijing") String region,
        @DefaultValue("flora-dev") String bucket,
        @DefaultValue("flora-local-ak") String accessKey,
        @DefaultValue("flora-local-sk") String secretKey,
        @DefaultValue("900") long presignTtlSeconds,
        @DefaultValue("15000") long timeoutMs,
        @DefaultValue("/tmp/flora-tos") String localDir) {
}
