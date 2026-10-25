package art.pilipala.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * pilipala worker 进程入口。
 *
 * <p>保持瘦：MQ 消息体只有 {@code job_id}，worker 读 job、调唯一 Anytoken 适配器、
 * 写 step 与结果、ack / 重试 / DLQ。画布拓扑、提示词拼装与计费确认都不在本进程里。
 *
 * <p>WP5 起有真实消费者：监听 {@code pilipala.jobs.execute}，prefetch 默认 1。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
