package com.pilipala.worker.job.domain;

/**
 * worker 会写进 job.error_code / job_step.error_code 的稳定码。
 *
 * <p>名字与 api 侧 {@code com.pilipala.api.common.ErrorCodes} 完全一致
 * （注册表见 docs/engineering/data-model/errors.md）。worker 不依赖 api 模块，
 * 所以这里留一份同名常量，不允许各写各的。
 */
public final class WorkerErrorCodes {

    public static final String JOB_CANCELLED = "JOB_CANCELLED";
    public static final String JOB_FAILED = "JOB_FAILED";
    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    /** WP12：视频轮询到上限仍未进入成功终态（可重试）。 */
    public static final String GATEWAY_TIMEOUT = "GATEWAY_TIMEOUT";
    /** WP12：上游响应结构不合法，拿不到任务 id（不可重试）。 */
    public static final String GATEWAY_INVALID_RESPONSE = "GATEWAY_INVALID_RESPONSE";
    /** WP12：上游视频任务进入失败终态（不可重试）。 */
    public static final String UPSTREAM_VIDEO_FAILED = "UPSTREAM_VIDEO_FAILED";

    private WorkerErrorCodes() {
    }
}
