package art.pilipala.api.job.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.job.domain.VideoFallbackJob;
import art.pilipala.api.job.support.InMemoryJobStore;
import org.junit.jupiter.api.Test;

class VideoFallbackSchedulerTest {

    private final UUID jobId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID c4 = UUID.randomUUID();
    private final UUID c7 = UUID.randomUUID();

    @Test
    void failedJobMovesToNextTargetAndRequeues() {
        FakeRepository repo = new FakeRepository();
        repo.nextTarget = c7;
        InMemoryJobStore outbox = new InMemoryJobStore();
        VideoFallbackScheduler scheduler = new VideoFallbackScheduler(repo, outbox, 50);

        scheduler.tick();

        assertThat(repo.requeued).containsExactly(jobId);
        assertThat(repo.dead).isEmpty();
        assertThat(outbox.allOutbox()).hasSize(1);
        assertThat(outbox.allOutbox().get(0).payloadJson()).contains(jobId.toString());
    }

    @Test
    void noNextTargetMarksDeadAndDoesNotPublish() {
        FakeRepository repo = new FakeRepository();
        repo.nextTarget = null;
        InMemoryJobStore outbox = new InMemoryJobStore();
        VideoFallbackScheduler scheduler = new VideoFallbackScheduler(repo, outbox, 50);

        scheduler.tick();

        assertThat(repo.requeued).isEmpty();
        assertThat(repo.dead).containsExactly(jobId);
        assertThat(outbox.allOutbox()).isEmpty();
    }

    private final class FakeRepository implements VideoFallbackRepository {
        private UUID nextTarget;
        private final List<UUID> requeued = new ArrayList<>();
        private final List<UUID> dead = new ArrayList<>();

        @Override
        public List<VideoFallbackJob> findFailed(int limit) {
            return List.of(new VideoFallbackJob(jobId, productId, c4, "UPSTREAM_VIDEO_FAILED", 1, 3));
        }

        @Override
        public Optional<UUID> findNextTarget(UUID product, UUID currentTargetId) {
            assertThat(product).isEqualTo(productId);
            assertThat(currentTargetId).isEqualTo(c4);
            return Optional.ofNullable(nextTarget);
        }

        @Override
        public boolean requeue(UUID id, UUID oldTargetId, UUID nextTargetId) {
            assertThat(oldTargetId).isEqualTo(c4);
            assertThat(nextTargetId).isEqualTo(c7);
            requeued.add(id);
            return true;
        }

        @Override
        public boolean markDead(UUID id, UUID oldTargetId) {
            assertThat(oldTargetId).isEqualTo(c4);
            dead.add(id);
            return true;
        }
    }
}
