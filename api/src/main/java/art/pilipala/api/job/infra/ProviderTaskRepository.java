package art.pilipala.api.job.infra;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.job.domain.ProviderTask;

/**
 * provider_task 表的读取契约。
 *
 * <p>原文留痕由 worker 写入，api 只在运维后台读它（ADR 0005）：
 * {@code raw_status} / {@code raw_body} 绝不进 C 端响应。
 */
public interface ProviderTaskRepository {
    /** 某个任务关联的全部上游留痕，按创建时间升序。 */
    List<ProviderTask> findByJobId(UUID jobId);

    /**
     * 批量取若干任务的上游留痕（按创建时间升序）。
     *
     * <p>WP11 运维后台一次要展示一整页任务的留痕，用一次查询代替 N 次。
     */
    List<ProviderTask> findByJobIds(List<UUID> jobIds);
}
