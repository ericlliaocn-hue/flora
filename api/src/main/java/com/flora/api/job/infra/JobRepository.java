package com.flora.api.job.infra;

import java.util.Optional;
import java.util.UUID;

import com.flora.api.job.domain.Job;
import com.flora.api.job.domain.JobStatus;

/**
 * job 表的存取契约。Postgres 是权威存储，实现在 {@link JdbcJobRepository}。
 *
 * <p>定义成接口是为了让应用层用例能在没有数据库的情况下被测试：
 * 状态机测试用内存实现 + FakeTransport 覆盖成功 / 重试 / 取消 / dead。
 * SQL 只出现在 infra 的 JDBC 实现里。
 */
public interface JobRepository {

    /**
     * 插入一条 status=created 的 job。同一 {@code (user_id, idempotency_key)} 已有行时
     * 不插入（并发同键的安全网）。
     *
     * @param holdId 钱包冻结单；未冻结时传 null
     * @return 新建的 job；同键已存在时返回空
     */
    Optional<Job> insertIfAbsent(UUID userId, String capability, String model, String paramsJson, String idempotencyKey,
            UUID holdId);

    Optional<Job> findById(UUID jobId);

    /** 只查本人的 job：他人的 job 与不存在的 job 一样返回空。 */
    Optional<Job> findByIdAndUser(UUID jobId, UUID userId);

    /**
     * queued → running 且 attempt+1。只有仍处于 queued 才会成功。
     */
    Optional<Job> claim(UUID jobId);

    /**
     * 按 from → to 迁移状态。from 不匹配时返回 false（说明状态已被别人改过）。
     *
     * @param errorCode 迁移后写入的错误码；成功迁移时传 null 表示清空
     */
    boolean updateStatus(UUID jobId, JobStatus from, JobStatus to, String errorCode);

    /** 成功时写 result_object_keys。 */
    boolean updateResult(UUID jobId, String resultObjectKeysJson);

    /** 记录取消意图（重复取消不覆盖首次时间）。 */
    boolean requestCancel(UUID jobId);
}
