package art.pilipala.worker.job.domain;

import java.util.Locale;

/**
 * job_step.status 的取值（worker 侧副本）：一次 attempt 一行。
 */
public enum WorkerStepStatus {

    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
