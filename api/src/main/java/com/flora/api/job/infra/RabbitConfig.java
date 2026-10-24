package com.flora.api.job.infra;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明：只声明，不含任何业务逻辑。
 *
 * <p>队列名来自 {@code flora.job.*}（exchange / routing key / queue / DLQ）。
 * 主队列挂了死信交换机（默认交换机），死信队列按自己的名字被默认交换机路由。
 *
 * <p>这些名字只存在于配置与这里，禁止写进 job 行（ADR 0003）。
 */
@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange floraJobExchange(JobTransportProperties properties) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    public Queue floraJobQueue(JobTransportProperties properties) {
        return QueueBuilder.durable(properties.queue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(properties.deadLetterQueue())
                .build();
    }

    @Bean
    public Queue floraJobDeadLetterQueue(JobTransportProperties properties) {
        return QueueBuilder.durable(properties.deadLetterQueue()).build();
    }

    @Bean
    public Binding floraJobBinding(Queue floraJobQueue, DirectExchange floraJobExchange, JobTransportProperties properties) {
        return BindingBuilder.bind(floraJobQueue).to(floraJobExchange).with(properties.routingKey());
    }
}
