package com.pilipala.worker.gateway;

import java.util.List;

/**
 * 调上游的唯一出参：stub 与 http 两种模式都归一到这个形状。
 *
 * @param succeeded        本次 attempt 是否成功
 * @param retryable        失败后是否值得重试
 * @param errorCode        失败时的稳定码（写进 job.error_code / job_step.error_code）
 * @param outputObjectKeys 成功时的对象键列表（WP8 之前没有签名 URL）
 * @param upstreamRequestId 上游请求标识
 * @param rawStatus        上游状态：HTTP 状态码字符串，或 TIMEOUT / IO_ERROR
 * @param rawBodyJson      脱敏且截断的上游响应摘要（JSON 文本）
 */
public record AnytokenAttemptResult(
        boolean succeeded,
        boolean retryable,
        String errorCode,
        List<String> outputObjectKeys,
        String upstreamRequestId,
        String rawStatus,
        String rawBodyJson) {

    public static AnytokenAttemptResult success(List<String> outputObjectKeys, String upstreamRequestId, String rawStatus, String rawBodyJson) {
        return new AnytokenAttemptResult(true, false, null, outputObjectKeys, upstreamRequestId, rawStatus, rawBodyJson);
    }

    public static AnytokenAttemptResult failure(String errorCode, boolean retryable, String upstreamRequestId, String rawStatus, String rawBodyJson) {
        return new AnytokenAttemptResult(false, retryable, errorCode, List.of(), upstreamRequestId, rawStatus, rawBodyJson);
    }
}
