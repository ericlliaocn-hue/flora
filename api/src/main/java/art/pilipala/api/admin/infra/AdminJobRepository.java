package art.pilipala.api.admin.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.admin.domain.AdminJob;

/**
 * 运维后台的任务查询。SQL 只在 JDBC 实现里。
 */
public interface AdminJobRepository {
    /**
     * 按状态 / 用户过滤列出任务（联表 users 与 wallet_hold），按创建时间倒序。
     *
     * @param status 任务状态；为空时不过滤
     * @param userId 用户；为空时不过滤
     * @param limit  单页上限
     */
    List<AdminJob> findJobs(String status, UUID userId, int limit);

    /** 按 id 取一条任务（联表 users 与 wallet_hold）。 */
    Optional<AdminJob> findJob(UUID jobId);
}
