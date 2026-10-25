package art.pilipala.api.job.domain;

import java.util.Locale;

/**
 * job_step.status 的取值。一次 attempt 一行。
 *
 * <p>与 docs/engineering/data-model/jobs.md 的 CHECK 约束一致：
 * {@code running|succeeded|failed|cancelled}。
 */
public enum JobStepStatus {

    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    /** 小写形式，写 DB 用它。 */
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static JobStepStatus of(String raw) {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
