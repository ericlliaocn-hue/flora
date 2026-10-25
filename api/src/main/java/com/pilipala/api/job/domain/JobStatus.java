package com.pilipala.api.job.domain;

import java.util.Locale;

/**
 * job.status 的取值。
 *
 * <p>与 docs/engineering/data-model/jobs.md 的 CHECK 约束逐字一致：
 * {@code created|queued|running|succeeded|failed|cancelled|dead}。
 * DB 与 C 端都用小写形式。
 */
public enum JobStatus {

    CREATED,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    DEAD;

    /** 小写形式，写 DB 与出 C 端都用它。 */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 终态：成功、取消、dead。终态不再迁移。 */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == CANCELLED || this == DEAD;
    }

    public static JobStatus of(String raw) {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
