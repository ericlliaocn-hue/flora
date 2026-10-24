package com.flora.worker.job.domain;

/**
 * worker 会写进 job.error_code / job_step.error_code 的稳定码。
 *
 * <p>名字与 api 侧 {@code com.flora.api.common.ErrorCodes} 完全一致
 * （注册表见 docs/engineering/data-model/errors.md）。worker 不依赖 api 模块，
 * 所以这里留一份同名常量，不允许各写各的。
 */
public final class WorkerErrorCodes {

    public static final String JOB_CANCELLED = "JOB_CANCELLED";
    public static final String JOB_FAILED = "JOB_FAILED";
    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";

    private WorkerErrorCodes() {
    }
}
