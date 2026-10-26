package art.pilipala.api.auth.application;

import java.security.SecureRandom;

import art.pilipala.api.auth.domain.OtpPurpose;
import art.pilipala.api.auth.infra.AuthException;
import art.pilipala.api.auth.infra.EmailSender;
import art.pilipala.api.auth.infra.OtpRedisStore;
import art.pilipala.api.auth.infra.SmsSender;
import org.springframework.stereotype.Service;

/**
 * 发送与校验 OTP（仅存 Redis）。
 */
@Service
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpRedisStore store;
    private final SmsSender smsSender;
    private final EmailSender emailSender;

    public OtpService(OtpRedisStore store, SmsSender smsSender, EmailSender emailSender) {
        this.store = store;
        this.smsSender = smsSender;
        this.emailSender = emailSender;
    }

    public SendOtpResult send(String channel, String destination, OtpPurpose purpose) {
        if (store.onCooldown(purpose.redisValue(), destination)) {
            throw AuthException.otpRateLimited();
        }
        if (store.isOverHourlyLimit(destination) || store.isOverDailyLimit(destination)) {
            throw AuthException.otpRateLimited();
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        store.saveCode(purpose.redisValue(), destination, code);
        store.markCooldown(purpose.redisValue(), destination);
        store.recordSend(destination);
        if ("sms".equals(channel)) {
            smsSender.sendOtp(destination, purpose.redisValue(), code);
        } else {
            emailSender.sendOtp(destination, purpose.redisValue(), code);
        }
        return new SendOtpResult(300, 60);
    }

    public boolean verify(OtpPurpose purpose, String destination, String otp) {
        return store.verify(purpose.redisValue(), destination, otp);
    }

    public void consume(OtpPurpose purpose, String destination) {
        store.consume(purpose.redisValue(), destination);
    }

    public boolean verifyAndConsume(OtpPurpose purpose, String destination, String otp) {
        return store.verifyAndConsume(purpose.redisValue(), destination, otp);
    }

    public record SendOtpResult(int expiresIn, int cooldown) {
    }
}
