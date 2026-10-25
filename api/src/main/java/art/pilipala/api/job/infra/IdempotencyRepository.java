package art.pilipala.api.job.infra;

import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.job.domain.IdempotencyRecord;

/**
 * idempotency_record 表的存取契约。实现在 {@link MybatisIdempotencyRepository}。
 *
 * <p>唯一约束 {@code (user_id, key)}：同 key 重复 submit 返回同一 job_id，
 * 同 key 不同 body → IDEMPOTENCY_CONFLICT。
 */
public interface IdempotencyRepository {

    Optional<IdempotencyRecord> findByUserAndKey(UUID userId, String key);

    /** 写入首次 submit 的结果。 */
    void insert(UUID userId, String key, UUID jobId, String requestHash);
}
