package com.flora.api.job.application;

import com.flora.api.job.domain.Job;

/**
 * query 的成功载荷。不含 raw / provider 字段。
 *
 * @param message 友好短句；error_code 为空时也是 null
 * @param updatedAt ISO-8601 时间
 */
public record JobView(
        String jobId,
        String status,
        int attempt,
        int maxAttempts,
        String errorCode,
        String message,
        String updatedAt) {

    /**
     * 文案由调用方按 locale 解析好再传进来（见 {@code I18nMessageService}）：
     * DTO 自己不查表，也不认识 error_code。
     */
    public static JobView of(Job job, String message) {
        return new JobView(
                job.id().toString(),
                job.status().value(),
                job.attempt(),
                job.maxAttempts(),
                job.errorCode(),
                message,
                job.updatedAt().toString());
    }
}
