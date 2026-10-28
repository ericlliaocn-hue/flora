package art.pilipala.worker.job.domain;

import java.util.UUID;

/** resume 阶段的一次查询：job 仍是 running，step 仍是 running，provider_task 在 polling。 */
public record PollingAttempt(
        UUID jobId,
        UUID jobStepId,
        int attempt,
        int maxAttempts,
        String capability,
        String model,
        UUID targetId,
        UUID providerTaskId,
        String upstreamTaskId,
        int pollCount,
        String paramsJson) {
}
