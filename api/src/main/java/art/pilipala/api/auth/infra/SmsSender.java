package art.pilipala.api.auth.infra;

/**
 * 短信发送抽象（WP17：生产走阿里云 Dysmsapi）。
 */
public interface SmsSender {

    void sendOtp(String phoneE164, String purpose, String code);
}
