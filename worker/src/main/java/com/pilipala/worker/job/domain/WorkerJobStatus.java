package com.pilipala.worker.job.domain;

import java.util.Locale;

/**
 * job.status 的取值（worker 侧副本）。
 *
 * <p>与 api 侧 {@code com.pilipala.api.job.domain.JobStatus} 一致：两个模块互不依赖，
 * 状态字面量以 docs/engineering/data-model/jobs.md 的 CHECK 约束为准。
 */
public enum WorkerJobStatus {

    CREATED,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    DEAD;

    /** 小写形式，写 DB 与比较都用它。 */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 终态：成功、取消、dead。 */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == CANCELLED || this == DEAD;
    }
}
