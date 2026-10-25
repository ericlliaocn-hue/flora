package com.pilipala.api.job.web;

import com.pilipala.api.common.ApiResponse;
import com.pilipala.api.i18n.application.FriendlyErrorHandler;
import com.pilipala.api.i18n.application.I18nMessageService;
import com.pilipala.api.i18n.application.RequestLocaleResolver;
import com.pilipala.api.job.infra.JobException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Job 域异常 → 统一信封。
 *
 * <p>只处理 {@link JobException}；未登录（NotLoginException）与请求体解析失败由
 * {@code AuthExceptionHandler} 处理，避免两个 advice 抢同一个异常。
 *
 * <p>本 advice 排在最前：{@code AuthExceptionHandler} 的 {@code Exception.class}
 * 兜底更宽，若它先被问到就会把 {@code JOB_NOT_FOUND} 抢成泛化码。
 *
 * <p>任何情况下都不回显异常原文：短句一律由 {@link I18nMessageService} 按 locale 解析。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JobExceptionHandler extends FriendlyErrorHandler {

    public JobExceptionHandler(ObjectProvider<I18nMessageService> i18nProvider,
            ObjectProvider<RequestLocaleResolver> localeResolvers) {
        super(i18nProvider, localeResolvers);
    }

    @ExceptionHandler(JobException.class)
    public ApiResponse<Void> handleJob(JobException e, HttpServletRequest request) {
        return failure(e.errorCode(), request);
    }
}
