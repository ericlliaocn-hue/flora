package com.flora.api.job.application;

import java.util.List;

/**
 * result 的成功载荷。
 *
 * <p>先看 status 再读 outputs：只有 succeeded 时 outputs 才有意义。
 */
public record JobResultView(
        String jobId,
        String status,
        List<JobOutput> outputs,
        String errorCode,
        String message) {
}
