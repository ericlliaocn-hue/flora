package com.flora.api.job.application;

import com.flora.api.job.domain.Job;
import com.flora.api.job.infra.JobErrorMessages;

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

    public static JobView of(Job job, String locale) {
        return new JobView(
                job.id().toString(),
                job.status().value(),
                job.attempt(),
                job.maxAttempts(),
                job.errorCode(),
                job.errorCode() == null ? null : JobErrorMessages.resolve(job.errorCode(), locale),
                job.updatedAt().toString());
    }
}
