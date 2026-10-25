package art.pilipala.worker.job.consumer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.worker.job.application.ExecuteJob;
import art.pilipala.worker.job.domain.JobOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 消费 {@code pilipala.jobs.execute}：消息体只有 {@code job_id}。
 *
 * <p>本方法 **不是** {@code @Transactional}：complete 已经在独立的短事务里提交，
 * 所以这里抛 {@link AmqpRejectAndDontRequeueException} 只会让消息进死信队列，
 * 不会回滚数据库里已经写好的 {@code status = 'dead'}。
 *
 * <p>毒丸消息（JSON 损坏 / job_id 不是 UUID）同样直接进死信队列。
 */
@Component
public class JobRabbitConsumer {

    private static final Logger log = LoggerFactory.getLogger(JobRabbitConsumer.class);

    private final ExecuteJob executeJob;
    private final ObjectMapper mapper;

    public JobRabbitConsumer(ExecuteJob executeJob, ObjectMapper mapper) {
        this.executeJob = executeJob;
        this.mapper = mapper;
    }

    @RabbitListener(queues = "${pilipala.job.queue}")
    public void onMessage(Message message) {
        UUID jobId = parseJobId(message);
        JobOutcome outcome = executeJob.execute(jobId);
        if (outcome == JobOutcome.DEAD) {
            log.warn("job 已 dead，消息进死信队列：{}", jobId);
            throw new AmqpRejectAndDontRequeueException("job 已 dead：" + jobId);
        }
    }

    private UUID parseJobId(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            JsonNode node = mapper.readTree(body);
            return UUID.fromString(node.path("job_id").asText(null));
        } catch (Exception e) {
            log.warn("消息体无法解析 job_id，进死信队列：{}", body);
            throw new AmqpRejectAndDontRequeueException("消息体无法解析 job_id");
        }
    }
}
