package art.pilipala.api.auth.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OTP 通道配置：多短信供应商 + 自建 SMTP（密钥只走环境变量）。
 */
@ConfigurationProperties(prefix = "pilipala.auth")
public record AuthChannelProperties(SmsConfig sms, MailSmtp mail) {

    public record SmsConfig(String provider, SmsAliyun aliyun, SmsSmsbao smsbao) {
    }

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

    public record SmsSmsbao(
            String username,
            String apiKey,
            String goodsId,
            String contentTemplate,
            String baseUrl) {
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
