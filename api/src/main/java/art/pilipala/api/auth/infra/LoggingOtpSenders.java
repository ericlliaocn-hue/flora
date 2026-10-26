package art.pilipala.api.auth.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * local / dev profile：邮件验证码只打日志（短信请设 {@code PILIPALA_SMS_PROVIDER=logging}）。
 */
@Configuration
@Profile({"local", "dev"})
public class LoggingOtpSenders {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSenders.class);

    @Bean
    @Primary
    EmailSender loggingEmailSender() {
        return (email, purpose, code) ->
                log.info("[WP17 OTP local] EMAIL purpose={} email={} code={}", purpose, email, code);
    }
}
