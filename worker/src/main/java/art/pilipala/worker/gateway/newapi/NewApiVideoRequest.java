package art.pilipala.worker.gateway.newapi;

/**
 * 提交给 NewAPI 的视频请求。字段是 target 侧先归一化后的最小集；
 * 长尾参数留在 {@code paramsJson}，由上层映射，不在客户端里按 model 名写分支。
 */
public record NewApiVideoRequest(
        String model,
        String prompt,
        int durationSeconds,
        String size,
        String paramsJson,
        String requestId,
        long deadlineAtEpochMs) {
}
