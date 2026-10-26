package art.pilipala.api.auth.infra;

/**
 * 邮件发送抽象（WP17：自建 SMTP）。
 */
public interface EmailSender {

    void sendOtp(String email, String purpose, String code);
}
