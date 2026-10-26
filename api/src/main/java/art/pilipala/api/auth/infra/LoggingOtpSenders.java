package art.pilipala.api.auth.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * local / dev profile：验证码只打日志，便于 curl 验收（生产禁止明文 OTP 日志）。
 */
@Configuration
@Profile({"local", "dev"})
public class LoggingOtpSenders {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSenders.class);

    @Bean
    @Primary
    SmsSender loggingSmsSender() {
        return (phone, purpose, code) ->
                log.info("[WP17 OTP local] SMS purpose={} phone={} code={}", purpose, phone, code);
    }

    @Bean
    @Primary
    EmailSender loggingEmailSender() {
        return (email, purpose, code) ->
                log.info("[WP17 OTP local] EMAIL purpose={} email={} code={}", purpose, email, code);
    }
}
