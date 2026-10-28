package art.pilipala.api.job.infra;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 按 {@code provider_task.next_poll_at} 把到期的视频 polling 任务重新投递到 worker。
 *
 * <p>只插入 outbox（job_id），不直接发 MQ；投递失败由 outbox 自己重试。
 * 真正的 query/store 在 worker；api 不调 NewAPI。
 */
@Component
public class ProviderPollScheduler {

    private final ProviderPollRepository polls;
    private final OutboxRepository outbox;
    private final int batchSize;
    private final int leaseSeconds;

    public ProviderPollScheduler(ProviderPollRepository polls, OutboxRepository outbox,
            @Value("${pilipala.provider.poll-batch-size:50}") int batchSize,
            @Value("${pilipala.provider.poll-lease-seconds:30}") int leaseSeconds) {
        this.polls = polls;
        this.outbox = outbox;
        this.batchSize = Math.max(1, batchSize);
        this.leaseSeconds = Math.max(1, leaseSeconds);
    }

    @Scheduled(fixedDelayString = "${pilipala.provider.poll-scan-ms:2000}")
    public void tick() {
        for (UUID jobId : polls.claimDue(batchSize, leaseSeconds)) {
            UUID eventId = UUID.randomUUID();
            outbox.insertPending(eventId, "job", jobId, JobMessagePayload.of(jobId, eventId));
        }
    }
}
