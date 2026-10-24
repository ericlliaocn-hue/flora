package com.flora.api.auth.web;

import cn.dev33.satoken.exception.NotLoginException;
import com.flora.api.auth.infra.AuthErrorMessages;
import com.flora.api.auth.infra.AuthException;
import com.flora.api.common.ApiResponse;
import com.flora.api.common.ErrorCodes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证异常 → 统一信封。
 *
 * <p>规则：
 * <ul>
 *   <li>没有会话（{@code NotLoginException}）→ {@code UNAUTHORIZED}</li>
 *   <li>认证用例抛出的 {@link AuthException} → 它自带的 error_code</li>
 *   <li>请求体解析不了 → {@code VALIDATION_ERROR}</li>
 * </ul>
 *
 * <p>任何情况下都不回显异常原文：短句一律由 {@link AuthErrorMessages} 按 locale 解析。
 */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ApiResponse<Void> handleNotLogin(NotLoginException e, HttpServletRequest request) {
        return failure(ErrorCodes.UNAUTHORIZED, request);
    }

    @ExceptionHandler(AuthException.class)
    public ApiResponse<Void> handleAuth(AuthException e, HttpServletRequest request) {
        return failure(e.errorCode(), request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ApiResponse<Void> handleBadRequest(Exception e, HttpServletRequest request) {
        return failure(ErrorCodes.VALIDATION_ERROR, request);
    }

    private ApiResponse<Void> failure(String errorCode, HttpServletRequest request) {
        String message = AuthErrorMessages.resolve(errorCode, request.getHeader("Accept-Language"));
        return ApiResponse.error(errorCode, message);
    }
}
