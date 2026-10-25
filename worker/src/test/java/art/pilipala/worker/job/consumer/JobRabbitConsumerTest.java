package art.pilipala.worker.job.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.worker.job.application.ExecuteJob;
import art.pilipala.worker.job.domain.JobOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;

/**
 * 消费者：坏消息与 dead 都要进死信队列（requeue 到 DLQ，而不是无限重试）。
 */
class JobRabbitConsumerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecuteJob executeJob = mock(ExecuteJob.class);
    private final JobRabbitConsumer consumer = new JobRabbitConsumer(executeJob, mapper);

    @Test
    void deadJobIsRejectedToTheDeadLetterQueue() {
        UUID jobId = UUID.randomUUID();
        when(executeJob.execute(jobId)).thenReturn(JobOutcome.DEAD);

        assertThatThrownBy(() -> consumer.onMessage(message(jobId)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void normalOutcomeIsAcknowledged() {
        UUID jobId = UUID.randomUUID();
        when(executeJob.execute(jobId)).thenReturn(JobOutcome.SUCCEEDED);

        consumer.onMessage(message(jobId));
    }

    @Test
    void poisonMessageIsRejectedToTheDeadLetterQueue() {
        assertThatThrownBy(() -> consumer.onMessage(raw("not json")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> consumer.onMessage(raw("{\"job_id\":\"not-a-uuid\"}")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private Message message(UUID jobId) {
        return raw("{\"job_id\":\"" + jobId + "\",\"event_id\":\"e-1\"}");
    }

    private Message raw(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8));
    }
}
