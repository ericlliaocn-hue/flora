package art.pilipala.api.job.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import art.pilipala.api.job.support.InMemoryJobStore;
import org.junit.jupiter.api.Test;

class ProviderPollSchedulerTest {

    @Test
    void dueVideoPollsArePublishedToTheOutbox() {
        UUID jobId = UUID.randomUUID();
        InMemoryJobStore outbox = new InMemoryJobStore();
        ProviderPollRepository polls = (limit, leaseSeconds) -> List.of(jobId);
        ProviderPollScheduler scheduler = new ProviderPollScheduler(polls, outbox, 50, 30);

        scheduler.tick();

        assertThat(outbox.allOutbox()).hasSize(1);
        assertThat(outbox.allOutbox().get(0).aggregateId()).isEqualTo(jobId);
        assertThat(outbox.allOutbox().get(0).payloadJson()).contains(jobId.toString());
    }

    @Test
    void noDuePollsMeansNoOutboxRows() {
        InMemoryJobStore outbox = new InMemoryJobStore();
        ProviderPollRepository polls = (limit, leaseSeconds) -> List.of();
        ProviderPollScheduler scheduler = new ProviderPollScheduler(polls, outbox, 50, 30);

        scheduler.tick();

        assertThat(outbox.allOutbox()).isEmpty();
    }
}
