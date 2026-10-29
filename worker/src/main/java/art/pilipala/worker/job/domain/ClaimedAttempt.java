package art.pilipala.worker.job.domain;

import java.util.UUID;

/**
 * 一次被领取的 attempt：claim 事务的产物，喂给上游调用与收尾事务。
 *
 * @param jobStepId 本次 attempt 的 job_step 行
 */
public record ClaimedAttempt(
        UUID jobId,
        UUID jobStepId,
        int attempt,
        int maxAttempts,
        String capability,
        String model,
        UUID productId,
        UUID targetId,
        String gatewayGroup,
        String gatewayModel,
        Integer gatewayTimeoutSeconds,
        String paramsJson) {
}
