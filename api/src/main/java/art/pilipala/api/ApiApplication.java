package art.pilipala.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * pilipala 业务 API 进程入口。
 *
 * <p>WP1 是进程骨架与健康检查；WP3 起有 users 表与 Sa-Token 认证；
 * WP4 起有 job 域（状态机 + outbox + RabbitMQ 投递）。
 *
 * <p>{@code @EnableScheduling} 供 outbox dispatcher 定时投递；
 * {@code @ConfigurationPropertiesScan} 供 {@code pilipala.job.*} 绑定。
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
