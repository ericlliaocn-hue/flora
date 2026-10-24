package com.flora.api.auth.web;

import cn.dev33.satoken.exception.NotLoginException;
import com.flora.api.auth.infra.AuthException;
import com.flora.api.common.ApiResponse;
import com.flora.api.common.ErrorCodes;
import com.flora.api.i18n.application.FriendlyErrorHandler;
import com.flora.api.i18n.application.I18nMessageService;
import com.flora.api.i18n.application.RequestLocaleResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 认证异常 → 统一信封。
 *
 * <p>规则：
 * <ul>
 *   <li>没有会话（{@code NotLoginException}）→ {@code UNAUTHORIZED}</li>
 *   <li>认证用例抛出的 {@link AuthException} → 它自带的 error_code</li>
 *   <li>请求体解析不了 → {@code VALIDATION_ERROR}</li>
 *   <li>其它未预期异常 → {@code PROVIDER_UNAVAILABLE}（兜底，原文只进服务端日志）</li>
 * </ul>
 *
 * <p>任何情况下都不回显异常原文：短句一律由 {@link I18nMessageService} 按 locale 解析。
 *
 * <p>顺序：本 advice 排在最后，{@code JobExceptionHandler} 先处理 Job 域异常，
 * 免得 {@code Exception.class} 兜底把 {@code JOB_NOT_FOUND} 抢走。
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class AuthExceptionHandler extends FriendlyErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

    public AuthExceptionHandler(ObjectProvider<I18nMessageService> i18nProvider,
            ObjectProvider<RequestLocaleResolver> localeResolvers) {
        super(i18nProvider, localeResolvers);
    }

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

    /**
     * 兜底：未预期的数据库异常、运行时异常只进服务端日志，
     * C 端恒为 HTTP 200 + 稳定 error_code + 友好短句，绝不输出原文、SQL 或堆栈。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> onUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), e);
        return failure(ErrorCodes.PROVIDER_UNAVAILABLE, request);
    }
}
