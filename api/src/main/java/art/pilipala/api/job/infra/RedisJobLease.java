package art.pilipala.api.job.infra;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 短锁：{@code SET job:lease:{job_id} {worker_id} NX EX {ttl}}。
 *
 * <p>TTL 默认 60 秒，可用 {@code FLORA_JOB_LEASE_TTL_SECONDS} 调。
 * 锁丢了但 DB 仍为 running 的孤儿回收属于运维策略，本 WP 不做（见 brief 的「不拍板」）。
 */
@Component
public class RedisJobLease implements JobLease {

    private static final String KEY_PREFIX = "job:lease:";

    /** 只有持有者本人能删锁，避免误删别人的租约。 */
    private static final DefaultRedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final long ttlSeconds;

    public RedisJobLease(StringRedisTemplate redis, @Value("${pilipala.job.lease-ttl-seconds:60}") long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public boolean acquire(UUID jobId, String workerId) {
        Boolean acquired = redis.opsForValue().setIfAbsent(key(jobId), workerId, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void release(UUID jobId, String workerId) {
        redis.execute(RELEASE_IF_OWNER, List.of(key(jobId)), workerId);
    }

    private static String key(UUID jobId) {
        return KEY_PREFIX + jobId;
    }
}
