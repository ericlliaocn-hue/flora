package art.pilipala.api.auth.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 自建 SMTP 发验证码邮件。
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final AuthChannelProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, AuthChannelProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendOtp(String email, String purpose, String code) {
        AuthChannelProperties.MailSmtp mail = properties.mail();
        if (mail == null || mail.host() == null || mail.host().isBlank()) {
            log.error("SMTP 未配置 purpose={} email={}", purpose, maskEmail(email));
            throw AuthException.providerUnavailable();
        }
        String from = mail.from() != null && !mail.from().isBlank() ? mail.from() : mail.username();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("pilipala 验证码");
        message.setText("您的验证码是 " + code + "，5 分钟内有效。如非本人操作请忽略。");
        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("SMTP 发送失败 email={}", maskEmail(email), e);
            throw AuthException.providerUnavailable();
        }
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.charAt(0) + "***" + email.substring(at) : "***";
    }
}
