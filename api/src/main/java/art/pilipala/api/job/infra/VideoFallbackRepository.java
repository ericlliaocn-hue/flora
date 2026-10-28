package art.pilipala.api.job.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.job.domain.VideoFallbackJob;

/** 产品级 fallback 的仓储端口；只读候选 + 原子 requeue/markDead。 */
public interface VideoFallbackRepository {

    List<VideoFallbackJob> findFailed(int limit);

    Optional<UUID> findNextTarget(UUID productId, UUID currentTargetId);

    boolean requeue(UUID jobId, UUID oldTargetId, UUID nextTargetId);

    boolean markDead(UUID jobId, UUID oldTargetId);
}
