package com.flora.api.common;

/**
 * 已登记的稳定错误码。
 *
 * <p>完整注册表见 docs/engineering/data-model/errors.md。本文件只收录已登记的码，
 * 不发明新码——需要新码时先改那份文档。
 *
 * <p>WP4 启用的码：UNAUTHORIZED、VALIDATION_ERROR、JOB_NOT_FOUND、JOB_CANCELLED、
 * IDEMPOTENCY_CONFLICT、JOB_FAILED；WP5 起启用 PROVIDER_UNAVAILABLE；
 * WP6 起启用 WALLET_INSUFFICIENT。
 */
public final class ErrorCodes {

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String JOB_NOT_FOUND = "JOB_NOT_FOUND";
    public static final String JOB_CANCELLED = "JOB_CANCELLED";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String JOB_FAILED = "JOB_FAILED";
    /** WP5 起启用：上游不可用或未识别（worker 写进 job.error_code）。 */
    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    /** WP6 起启用：冻结失败，余额不足；不入队。 */
    public static final String WALLET_INSUFFICIENT = "WALLET_INSUFFICIENT";

    private ErrorCodes() {
    }
}
