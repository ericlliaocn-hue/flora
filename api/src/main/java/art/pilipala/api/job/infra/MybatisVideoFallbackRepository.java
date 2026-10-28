package art.pilipala.api.job.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import art.pilipala.api.job.domain.VideoFallbackJob;

@Repository
public class MybatisVideoFallbackRepository implements VideoFallbackRepository {

    private final VideoFallbackMapper mapper;

    public MybatisVideoFallbackRepository(VideoFallbackMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<VideoFallbackJob> findFailed(int limit) {
        return mapper.findFailed(limit);
    }

    @Override
    public Optional<UUID> findNextTarget(UUID productId, UUID currentTargetId) {
        return Optional.ofNullable(mapper.findNextTarget(productId, currentTargetId));
    }

    @Override
    public boolean requeue(UUID jobId, UUID oldTargetId, UUID nextTargetId) {
        return mapper.requeue(jobId, oldTargetId, nextTargetId) > 0;
    }

    @Override
    public boolean markDead(UUID jobId, UUID oldTargetId) {
        return mapper.markDead(jobId, oldTargetId) > 0;
    }
}
