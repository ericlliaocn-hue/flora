package art.pilipala.worker.gateway.newapi;

/**
 * NewAPI 任务状态。{@code terminal=false} 时调用方应继续轮询；{@code failed} 时不再重试。
 *
 * @param status    上游原始状态（可能是 {@code FAILED: <message>}）
 * @param succeeded 是否成功且已拿到成片 URL
 * @param terminal  是否终态
 * @param retryable 失败是否值得重试（仅失败时有意义）
 * @param outputUrl 成功时的成片直链（{@code object} 字段）
 * @param errorCode 失败时的稳定码
 * @param message   上游失败短句，仅内部排障；不进 C 端
 */
public record NewApiTaskStatus(
        String taskId,
        String status,
        boolean succeeded,
        boolean terminal,
        boolean retryable,
        String outputUrl,
        String errorCode,
        String message,
        String rawStatus,
        String rawBody) {

    public static NewApiTaskStatus running(String taskId, String status, String rawStatus, String rawBody) {
        return new NewApiTaskStatus(taskId, status, false, false, false, null, null, null, rawStatus, rawBody);
    }

    public static NewApiTaskStatus succeeded(String taskId, String status, String outputUrl, String rawStatus, String rawBody) {
        return new NewApiTaskStatus(taskId, status, true, true, false, outputUrl, null, null, rawStatus, rawBody);
    }

    public static NewApiTaskStatus failed(String taskId, String status, String errorCode, String message,
            String rawStatus, String rawBody) {
        return new NewApiTaskStatus(taskId, status, false, true, false, null, errorCode, message, rawStatus, rawBody);
    }

    public static NewApiTaskStatus transientFailure(String taskId, String status, String message,
            String rawStatus, String rawBody) {
        return new NewApiTaskStatus(taskId, status, false, false, true, null,
                "PROVIDER_UNAVAILABLE", message, rawStatus, rawBody);
    }
}
