package com.pilipala.api.job.infra;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pilipala.api.job.application.OutboxDispatchTick;

/**
 * outbox 的定时投递器：按配置的间隔调一次 {@link OutboxDispatchTick}。
 *
 * <p>只负责「什么时候扫」，投递语义与失败重试策略在用例里。
 */
@Component
public class OutboxDispatcher {

    private final OutboxDispatchTick tick;

    public OutboxDispatcher(OutboxDispatchTick tick) {
        this.tick = tick;
    }

    @Scheduled(fixedDelayString = "${pilipala.job.outbox.poll-ms:500}")
    public void dispatch() {
        tick.tick();
    }
}
