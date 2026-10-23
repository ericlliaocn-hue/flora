package com.flora.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * flora 业务 API 进程入口。
 *
 * <p>WP1 阶段只有进程骨架与健康检查：没有登录、没有业务接口、没有数据库。
 * 领域边界（{@code job} / {@code billing} / {@code gateway}）先留空包说明，
 * 对应实现分别在 WP2–WP5 落地。
 */
@SpringBootApplication
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
