package art.pilipala.api.auth.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code PILIPALA_SMS_PROVIDER=logging}：验证码只打服务端日志，供本地 / 开发验收（生产禁止）。
 */
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public void sendOtp(String phoneE164, String purpose, String code) {
        log.info("[WP17 OTP logging] SMS purpose={} phone={} code={}", purpose, phoneE164, code);
    }
}
