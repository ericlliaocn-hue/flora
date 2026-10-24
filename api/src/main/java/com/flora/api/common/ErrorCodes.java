package com.flora.api.common;

/**
 * 已登记的稳定错误码。
 *
 * <p>完整注册表见 docs/engineering/data-model/errors.md。本 WP 只用到其中两个；
 * 不发明新码——需要新码时先改那份文档。
 */
public final class ErrorCodes {

    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";

    private ErrorCodes() {
    }
}
