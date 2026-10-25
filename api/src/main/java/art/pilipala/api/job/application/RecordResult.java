package art.pilipala.api.job.application;

/**
 * 一次 attempt 收尾后的结果。
 *
 * @param status 迁移后的状态：succeeded / queued（重试）/ dead / cancelled
 * @param attempt 已经跑到第几次 attempt
 */
public record RecordResult(String jobId, String status, int attempt) {
}
