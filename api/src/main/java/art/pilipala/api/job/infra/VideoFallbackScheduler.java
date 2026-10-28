package art.pilipala.api.job.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import art.pilipala.api.job.domain.VideoFallbackJob;

/**
 * 产品级 fallback：failed 视频 job 按 model_route.priority 切下一个 target，重新入队。
 *
 * <p>不重算用户价、不二次冻结；全部 target 失败才把 job 置 dead，随后由钱包结算释放冻结。
 * 只处理上游任务失败 / 超时 / 网关不可用；参数/协议错误直接保持 failed→dead 路径。
 */
@Component
public class VideoFallbackScheduler {

    private final VideoFallbackRepository fallbacks;
    private final OutboxRepository outbox;
    private final int batchSize;

    public VideoFallbackScheduler(VideoFallbackRepository fallbacks, OutboxRepository outbox,
            @Value("${pilipala.video.fallback-batch-size:50}") int batchSize) {
        this.fallbacks = fallbacks;
        this.outbox = outbox;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${pilipala.video.fallback-scan-ms:5000}")
    public void tick() {
        for (VideoFallbackJob job : fallbacks.findFailed(batchSize)) {
            Optional<UUID> nextTarget = fallbacks.findNextTarget(job.productId(), job.targetId());
            if (nextTarget.isEmpty()) {
                fallbacks.markDead(job.id(), job.targetId());
                continue;
            }
            if (fallbacks.requeue(job.id(), job.targetId(), nextTarget.get())) {
                UUID eventId = UUID.randomUUID();
                outbox.insertPending(eventId, "job", job.id(), JobMessagePayload.of(job.id(), eventId));
            }
        }
    }
}
