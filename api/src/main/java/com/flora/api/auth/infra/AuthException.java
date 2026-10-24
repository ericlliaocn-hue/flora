package com.flora.api.auth.infra;

import com.flora.api.common.ErrorCodes;

/**
 * 带稳定 error_code 的认证异常。
 *
 * <p>异常本身不携带可出站的文案：短句统一由 {@code I18nMessageService} 按 locale 解析，
 * 禁止把 {@code e.getMessage()} 直接回给客户端。
 */
public class AuthException extends RuntimeException {

    private final String errorCode;

    private AuthException(String errorCode) {
        super(errorCode, null, false, false);
        this.errorCode = errorCode;
    }

    public static AuthException unauthorized() {
        return new AuthException(ErrorCodes.UNAUTHORIZED);
    }

    public static AuthException validation() {
        return new AuthException(ErrorCodes.VALIDATION_ERROR);
    }

    public String errorCode() {
        return errorCode;
    }
}
