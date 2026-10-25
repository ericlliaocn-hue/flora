package com.pilipala.worker.job.consumer;

import com.pilipala.worker.job.infra.WorkerJobProperties;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明：只声明，不含业务逻辑。
 *
 * <p>与 api 侧声明的是同一套（幂等持久化）：exchange {@code pilipala.jobs}、
 * 主队列 {@code pilipala.jobs.execute}、死信队列 {@code pilipala.jobs.execute.dlq}。
 * worker 先于 api 启动也不会报错。
 */
@Configuration
public class WorkerRabbitConfig {

    @Bean
    public DirectExchange floraJobExchange(WorkerJobProperties properties) {
        return new DirectExchange(properties.exchange(), true, false);
    }

    @Bean
    public Queue floraJobQueue(WorkerJobProperties properties) {
        return QueueBuilder.durable(properties.queue())
                .deadLetterExchange("")
                .deadLetterRoutingKey(properties.deadLetterQueue())
                .build();
    }

    @Bean
    public Queue floraJobDeadLetterQueue(WorkerJobProperties properties) {
        return QueueBuilder.durable(properties.deadLetterQueue()).build();
    }

    @Bean
    public Binding floraJobBinding(Queue floraJobQueue, DirectExchange floraJobExchange, WorkerJobProperties properties) {
        return BindingBuilder.bind(floraJobQueue).to(floraJobExchange).with(properties.routingKey());
    }
}
