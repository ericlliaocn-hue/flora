package art.pilipala.api.auth.infra;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * OTP 仅存 Redis（WP17）。key 约定见 feature brief。
 */
@Component
public class OtpRedisStore {

    static final Duration OTP_TTL = Duration.ofSeconds(300);
    static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);
    static final int MAX_VERIFY_ATTEMPTS = 5;
    static final int HOURLY_LIMIT = 10;
    static final int DAILY_LIMIT = 20;

    private final StringRedisTemplate redis;

    public OtpRedisStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void saveCode(String purpose, String destination, String code) {
        String key = otpKey(purpose, destination);
        redis.opsForValue().set(key, code, OTP_TTL);
        redis.opsForValue().set(attemptsKey(purpose, destination), "0", OTP_TTL);
    }

    public boolean onCooldown(String purpose, String destination) {
        return Boolean.TRUE.equals(redis.hasKey(cooldownKey(purpose, destination)));
    }

    public void markCooldown(String purpose, String destination) {
        redis.opsForValue().set(cooldownKey(purpose, destination), "1", COOLDOWN_TTL);
    }

    public boolean isOverHourlyLimit(String destination) {
        String key = "pilipala:otp:rate:h:" + destination;
        String raw = redis.opsForValue().get(key);
        long count = raw == null ? 0 : Long.parseLong(raw);
        return count >= HOURLY_LIMIT;
    }

    public boolean isOverDailyLimit(String destination) {
        String key = "pilipala:otp:rate:d:" + destination;
        String raw = redis.opsForValue().get(key);
        long count = raw == null ? 0 : Long.parseLong(raw);
        return count >= DAILY_LIMIT;
    }

    public void recordSend(String destination) {
        String hourKey = "pilipala:otp:rate:h:" + destination;
        Long hourCount = redis.opsForValue().increment(hourKey);
        if (hourCount != null && hourCount == 1L) {
            redis.expire(hourKey, Duration.ofHours(1));
        }
        String dayKey = "pilipala:otp:rate:d:" + destination;
        Long dayCount = redis.opsForValue().increment(dayKey);
        if (dayCount != null && dayCount == 1L) {
            redis.expire(dayKey, secondsUntilMidnight());
        }
    }

    /**
     * 校验验证码；成功则消费并删除。失败则累加次数，超限作废。
     *
     * @return true 表示校验通过且已消费
     */
    public boolean verifyAndConsume(String purpose, String destination, String otp) {
        String key = otpKey(purpose, destination);
        String stored = redis.opsForValue().get(key);
        if (stored == null || otp == null || !stored.equals(otp.trim())) {
            bumpAttempts(purpose, destination, key);
            return false;
        }
        redis.delete(key);
        redis.delete(attemptsKey(purpose, destination));
        return true;
    }

    private void bumpAttempts(String purpose, String destination, String otpKey) {
        String attemptsKey = attemptsKey(purpose, destination);
        Long attempts = redis.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts >= MAX_VERIFY_ATTEMPTS) {
            redis.delete(otpKey);
            redis.delete(attemptsKey);
        }
    }

    private static String otpKey(String purpose, String destination) {
        return "pilipala:otp:" + purpose + ":" + destination;
    }

    private static String cooldownKey(String purpose, String destination) {
        return "pilipala:otp:cooldown:" + purpose + ":" + destination;
    }

    private static String attemptsKey(String purpose, String destination) {
        return "pilipala:otp:attempts:" + purpose + ":" + destination;
    }

    private static Duration secondsUntilMidnight() {
        var now = LocalDate.now(ZoneId.systemDefault());
        var midnight = now.plusDays(1).atStartOfDay(ZoneId.systemDefault());
        long seconds = ChronoUnit.SECONDS.between(
                java.time.Instant.now(),
                midnight.toInstant());
        return Duration.ofSeconds(Math.max(seconds, TimeUnit.HOURS.toSeconds(1)));
    }
}
