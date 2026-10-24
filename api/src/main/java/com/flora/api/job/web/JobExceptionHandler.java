package com.flora.api.job.web;

import com.flora.api.common.ApiResponse;
import com.flora.api.job.infra.JobErrorMessages;
import com.flora.api.job.infra.JobException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Job 域异常 → 统一信封。
 *
 * <p>只处理 {@link JobException}；未登录（NotLoginException）与请求体解析失败由
 * {@code AuthExceptionHandler} 处理，避免两个 advice 抢同一个异常。
 *
 * <p>任何情况下都不回显异常原文：短句一律由 {@link JobErrorMessages} 按 locale 解析。
 */
@RestControllerAdvice
public class JobExceptionHandler {

    @ExceptionHandler(JobException.class)
    public ApiResponse<Void> handleJob(JobException e, HttpServletRequest request) {
        String locale = JobErrorMessages.localeOf(request.getHeader("Accept-Language"));
        return ApiResponse.error(e.errorCode(), JobErrorMessages.resolve(e.errorCode(), locale));
    }
}
