package com.flora.api.common;

/**
 * 已登记的稳定错误码。
 *
 * <p>完整注册表见 docs/engineering/data-model/errors.md。本文件只收录已登记的码，
 * 不发明新码——需要新码时先改那份文档。
 *
 * <p>WP4 启用的码：UNAUTHORIZED、VALIDATION_ERROR、JOB_NOT_FOUND、JOB_CANCELLED、
 * IDEMPOTENCY_CONFLICT、JOB_FAILED。没有钱包（WP6）也不启用 WALLET_INSUFFICIENT，
 * 没有上游（WP5）也不启用 PROVIDER_UNAVAILABLE。
 */
public final class ErrorCodes {

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String JOB_NOT_FOUND = "JOB_NOT_FOUND";
    public static final String JOB_CANCELLED = "JOB_CANCELLED";
    public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    public static final String JOB_FAILED = "JOB_FAILED";

    private ErrorCodes() {
    }
}
