package com.flora.api.job.infra;

import java.util.List;
import java.util.UUID;

import com.flora.api.job.domain.JobStep;
import com.flora.api.job.domain.JobStepStatus;

/**
 * job_step 表的存取契约。实现在 {@link JdbcJobStepRepository}。
 *
 * <p>一次 attempt 一行：claim 时插入 running，收尾时写终态。
 */
public interface JobStepRepository {

    /** 插入一行 status=running 的 step。 */
    JobStep start(UUID jobId, int attempt);

    /** 收尾：写 step 的终态、错误码与结束时间。 */
    void end(UUID jobId, int attempt, JobStepStatus status, String errorCode);

    /**
     * 批量取若干任务的全部 step（按 attempt 升序）。
     *
     * <p>WP11 运维后台一次要展示一整页任务的步骤，用一次查询代替 N 次。
     */
    List<JobStep> findByJobIds(List<UUID> jobIds);
}
