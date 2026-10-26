package art.pilipala.api.auth.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云短信与自建 SMTP 配置（密钥只走环境变量）。
 */
@ConfigurationProperties(prefix = "pilipala.auth")
public record AuthChannelProperties(
        SmsAliyun sms,
        MailSmtp mail) {

    public record SmsAliyun(
            String accessKeyId,
            String accessKeySecret,
            String signName,
            String templateRegister,
            String templateLogin,
            String templateRebind,
            String templateReset,
            String endpoint) {
    }

    public record MailSmtp(
            String host,
            int port,
            String username,
            String password,
            String from,
            boolean starttls) {
    }
}
