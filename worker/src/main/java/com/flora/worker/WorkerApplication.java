package com.flora.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * flora worker 进程入口。
 *
 * <p>保持瘦：MQ 消息体只有 {@code job_id}，worker 读 job、调 Anytoken、写 step 与结果、
 * ack / 重试 / DLQ。画布拓扑、提示词拼装与计费确认都不在本进程里。
 *
 * <p>WP1 阶段没有真实消费者，进程必须能独立启动并报健康；
 * RabbitMQ 接入与消费者在 WP3 落地。
 */
@SpringBootApplication
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
