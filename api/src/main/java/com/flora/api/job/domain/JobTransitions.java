package com.flora.api.job.domain;

import com.flora.api.job.infra.JobException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 状态迁移的纯函数规则。非法迁移抛 {@link JobException} 并记日志，不静默改状态。
 *
 * <p>状态机（docs/engineering/data-model/jobs.md）：
 *
 * <pre>
 * created → queued → running → succeeded
 *                 ↘ failed ──(attempt &lt; max_attempts)→ queued
 *                 ↘ cancelled          （终态）
 *                 ↘ dead               （终态，attempt 用尽或不可重试）
 * </pre>
 *
 * <p>异常类型按 brief 的包布局放在 infra，所以本类依赖 infra 的 {@link JobException}；
 * 规则本身不依赖任何框架，也不含 Rabbit 协议字段（换 Transport 不改状态枚举）。
 */
public final class JobTransitions {

    private static final Logger log = LoggerFactory.getLogger(JobTransitions.class);

    private JobTransitions() {
    }

    /** created → queued。 */
    public static void requireQueued(JobStatus from) {
        require(from == JobStatus.CREATED, from, JobStatus.QUEUED);
    }

    /** queued → running。 */
    public static void requireRunning(JobStatus from) {
        require(from == JobStatus.QUEUED, from, JobStatus.RUNNING);
    }

    /** running → succeeded。 */
    public static void requireSucceeded(JobStatus from) {
        require(from == JobStatus.RUNNING, from, JobStatus.SUCCEEDED);
    }

    /** running → failed。 */
    public static void requireFailed(JobStatus from) {
        require(from == JobStatus.RUNNING, from, JobStatus.FAILED);
    }

    /** failed → queued（还能重试）。 */
    public static void requireRetry(JobStatus from) {
        require(from == JobStatus.FAILED, from, JobStatus.QUEUED);
    }

    /** failed → dead（attempt 用尽或不可重试）。 */
    public static void requireDead(JobStatus from) {
        require(from == JobStatus.FAILED, from, JobStatus.DEAD);
    }

    /** 非终态 → cancelled。 */
    public static void requireCancellable(JobStatus from) {
        require(!from.isTerminal(), from, JobStatus.CANCELLED);
    }

    /** 是否还能重试：attempt 还没用尽。 */
    public static boolean canRetry(int attempt, int maxAttempts) {
        return attempt < maxAttempts;
    }

    private static void require(boolean allowed, JobStatus from, JobStatus to) {
        if (allowed) {
            return;
        }
        log.warn("拒绝非法状态迁移 from={} to={}", from.value(), to.value());
        throw JobException.validation();
    }
}
