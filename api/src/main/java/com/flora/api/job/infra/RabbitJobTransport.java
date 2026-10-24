package com.flora.api.job.infra;

import com.flora.api.job.domain.OutboxMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 真 broker 的 Transport：把 outbox 的 payload 原样发到 exchange + routing key。
 *
 * <p>exchange / routing key / queue / DLQ 只存在于配置（{@code flora.job.*}）与
 * {@link RabbitConfig} 的声明里，不写进 job 行（ADR 0003）。
 *
 * <p>只在 {@code flora.job.transport=rabbit}（默认）时装配。
 */
@Component
@ConditionalOnProperty(name = "flora.job.transport", havingValue = "rabbit", matchIfMissing = true)
public class RabbitJobTransport implements JobMessageTransport {

    private final RabbitTemplate rabbit;
    private final JobTransportProperties properties;

    public RabbitJobTransport(RabbitTemplate rabbit, JobTransportProperties properties) {
        this.rabbit = rabbit;
        this.properties = properties;
    }

    @Override
    public void publish(OutboxMessage message) {
        rabbit.convertAndSend(properties.exchange(), properties.routingKey(), message.payloadJson(), amqpMessage -> {
            amqpMessage.getMessageProperties().setContentType("application/json");
            return amqpMessage;
        });
    }
}
