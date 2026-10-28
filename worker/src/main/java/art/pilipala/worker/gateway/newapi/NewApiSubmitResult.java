package art.pilipala.worker.gateway.newapi;

/**
 * NewAPI 提交结果。{@code accepted=true} 只表示拿到了上游 task id，不表示视频已成功。
 *
 * @param taskId    上游异步任务 id；accepted=false 时为空
 * @param status    上游原始状态（如 RUNNING）
 * @param errorCode 失败时的稳定码；accepted=true 时为空
 * @param retryable 失败是否值得重试
 * @param rawStatus HTTP 状态码或 CONFIG / TIMEOUT / IO_ERROR
 * @param rawBody   脱敏且截断的上游响应
 */
public record NewApiSubmitResult(
        boolean accepted,
        String taskId,
        String status,
        String errorCode,
        boolean retryable,
        String rawStatus,
        String rawBody) {

    public static NewApiSubmitResult accepted(String taskId, String status, String rawStatus, String rawBody) {
        return new NewApiSubmitResult(true, taskId, status, null, false, rawStatus, rawBody);
    }

    public static NewApiSubmitResult failure(String errorCode, boolean retryable, String rawStatus, String rawBody) {
        return new NewApiSubmitResult(false, null, null, errorCode, retryable, rawStatus, rawBody);
    }
}
