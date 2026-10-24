package com.flora.api.job.web;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.flora.api.auth.infra.AuthException;
import com.flora.api.auth.infra.AuthSessionService;
import com.flora.api.common.ApiResponse;
import com.flora.api.i18n.application.I18nDefaultCatalog;
import com.flora.api.i18n.application.RequestLocaleResolver;
import com.flora.api.job.application.CancelJob;
import com.flora.api.job.application.GetJob;
import com.flora.api.job.application.GetJobResult;
import com.flora.api.job.application.JobResultView;
import com.flora.api.job.application.JobView;
import com.flora.api.job.application.SubmitJob;
import com.flora.api.job.application.SubmitJobView;
import com.flora.api.job.infra.JobException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端任务接口：submit / query / result / cancel。
 *
 * <p>鉴权闸门由 WP3 的 {@code AuthPathPolicy} 覆盖整个 {@code /api/v1/**}，
 * 这里只负责从会话取 user_id（禁止信任请求体里的 user id）。
 *
 * <p>失败一律走统一信封（{@code ok=false} + {@code error_code} + {@code message}），
 * HTTP 状态码恒为 200——沿用 WP3 的约定，资源不存在也回 200 + JOB_NOT_FOUND。
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final AuthSessionService sessions;
    private final SubmitJob submitJob;
    private final GetJob getJob;
    private final GetJobResult getJobResult;
    private final CancelJob cancelJob;
    private final ObjectProvider<RequestLocaleResolver> localeResolvers;

    public JobController(AuthSessionService sessions, SubmitJob submitJob, GetJob getJob, GetJobResult getJobResult,
            CancelJob cancelJob, ObjectProvider<RequestLocaleResolver> localeResolvers) {
        this.sessions = sessions;
        this.submitJob = submitJob;
        this.getJob = getJob;
        this.getJobResult = getJobResult;
        this.cancelJob = cancelJob;
        this.localeResolvers = localeResolvers;
    }

    @PostMapping("/submit")
    public ApiResponse<SubmitJobView> submit(@RequestBody SubmitRequest request) {
        return ApiResponse.ok(submitJob.submit(
                currentUserId(),
                request.idempotencyKey(),
                request.capability(),
                request.model(),
                request.params(),
                request.clientJobId()));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<JobView> get(@PathVariable String jobId,
                                    @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        UUID userId = currentUserId();
        return ApiResponse.ok(getJob.get(userId, jobIdOf(jobId), locale(userId, acceptLanguage)));
    }

    @GetMapping("/{jobId}/result")
    public ApiResponse<JobResultView> result(@PathVariable String jobId,
                                              @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        UUID userId = currentUserId();
        return ApiResponse.ok(getJobResult.result(userId, jobIdOf(jobId), locale(userId, acceptLanguage)));
    }

    @PostMapping("/{jobId}/cancel")
    public ApiResponse<JobView> cancel(@PathVariable String jobId,
                                        @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        UUID userId = currentUserId();
        return ApiResponse.ok(cancelJob.cancel(userId, jobIdOf(jobId), locale(userId, acceptLanguage)));
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(sessions.currentUserId());
        } catch (IllegalArgumentException e) {
            throw AuthException.unauthorized();
        }
    }

    /** 路径上的 id 非法时按「不存在」处理，不泄露格式细节。 */
    private UUID jobIdOf(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw JobException.notFound();
        }
    }

    /**
     * 有效语言：登录用户的 {@code users.locale} 优先，其次 {@code Accept-Language}。
     *
     * <p>窄切片里没有 {@link RequestLocaleResolver} 时按请求头解析。
     */
    private String locale(UUID userId, String acceptLanguage) {
        RequestLocaleResolver resolver = localeResolvers.getIfAvailable();
        if (resolver == null) {
            return I18nDefaultCatalog.localeOf(acceptLanguage);
        }
        return resolver.resolve(userId, acceptLanguage);
    }

    /**
     * submit 请求体。
     *
     * @param clientJobId 客户端自己的追踪 id；本 WP 收下但不落库
     */
    public record SubmitRequest(String idempotencyKey, String capability, String model, JsonNode params, String clientJobId) {
    }
}
