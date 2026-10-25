package com.pilipala.api.job.application;

import java.util.List;

/**
 * 一次 attempt 的结果。
 *
 * @param succeeded        是否成功
 * @param errorCode        失败时的稳定码（写进 job.error_code / job_step.error_code）
 * @param retryable        失败后是否值得重试
 * @param resultObjectKeys 成功时的对象键列表
 */
public record AttemptOutcome(boolean succeeded, String errorCode, boolean retryable, List<String> resultObjectKeys) {

    public static AttemptOutcome success(List<String> objectKeys) {
        return new AttemptOutcome(true, null, false, objectKeys == null ? List.of() : List.copyOf(objectKeys));
    }

    public static AttemptOutcome failure(String errorCode, boolean retryable) {
        return new AttemptOutcome(false, errorCode, retryable, List.of());
    }
}
