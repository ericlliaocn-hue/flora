package art.pilipala.api.auth.infra;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 {@code PILIPALA_SMS_PROVIDER} 选择短信实现：smsbao | aliyun | logging。
 */
@Configuration
public class SmsSenderConfiguration {

    @Bean
    SmsSender smsSender(AuthChannelProperties properties) {
        AuthChannelProperties.SmsConfig sms = properties.sms();
        String provider = sms == null || sms.provider() == null ? "smsbao" : sms.provider().trim().toLowerCase();
        return switch (provider) {
            case "smsbao" -> new SmsBaoSmsSender(sms == null ? null : sms.smsbao());
            case "aliyun" -> new AliyunSmsSender(sms == null ? null : sms.aliyun());
            case "logging" -> new LoggingSmsSender();
            default -> throw new IllegalStateException("Unknown PILIPALA_SMS_PROVIDER: " + provider);
        };
    }
}
