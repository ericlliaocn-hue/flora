package art.pilipala.worker.job.domain;

import java.util.UUID;

/** worker 打开的 provider_task 最小视图（用于 resume 查询）。 */
public record PollingTask(
        UUID providerTaskId,
        UUID jobId,
        UUID jobStepId,
        UUID targetId,
        String upstreamTaskId,
        String status,
        int pollCount,
        String rawStatus,
        String rawBodyJson) {
}
