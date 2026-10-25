package com.pilipala.api.job.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Redis 短锁的键约定与 TTL（不连 Redis，只断言调用参数）。
 */
class RedisJobLeaseTest {

    @Test
    void acquiresWithJobScopedKeyAndConfiguredTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        RedisJobLease lease = new RedisJobLease(redis, 60);
        UUID jobId = UUID.randomUUID();

        assertThat(lease.acquire(jobId, "worker-1")).isTrue();
        verify(operations).setIfAbsent("job:lease:" + jobId, "worker-1", Duration.ofSeconds(60));
    }

    @Test
    void leaseIsNotTakenWhenTheKeyExists() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(new RedisJobLease(redis, 60).acquire(UUID.randomUUID(), "worker-1")).isFalse();
    }
}
