package art.pilipala.worker.job.infra;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 短锁：{@code SET job:lease:{job_id} {worker_id} NX EX {ttl}}。
 *
 * <p>TTL 默认 60 秒，可用 {@code PILIPALA_JOB_LEASE_TTL_SECONDS} 调。
 * 释放用 Lua 做「只有持有者本人能删」，避免误删别人的租约。
 */
@Component
public class RedisWorkerJobLease implements WorkerJobLease {

    private static final String KEY_PREFIX = "job:lease:";

    private static final DefaultRedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;
    private final long ttlSeconds;

    public RedisWorkerJobLease(StringRedisTemplate redis, WorkerJobProperties properties) {
        this.redis = redis;
        this.ttlSeconds = properties.leaseTtlSeconds();
    }

    @Override
    public boolean acquire(UUID jobId, String workerId) {
        Boolean acquired = redis.opsForValue().setIfAbsent(KEY_PREFIX + jobId, workerId, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void release(UUID jobId, String workerId) {
        redis.execute(RELEASE_IF_OWNER, List.of(KEY_PREFIX + jobId), workerId);
    }
}
