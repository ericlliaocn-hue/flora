package com.flora.api.job.application;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.flora.api.job.domain.OutboxMessage;
import com.flora.api.job.infra.JobMessageTransport;
import com.flora.api.job.infra.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 投递一批 outbox 消息。
 *
 * <p>成功 → outbox 置 sent；失败仍留在 pending 等下一轮重试，连续失败到上限
 * （默认 10 次）才置 failed 并打错误日志。
 *
 * <p>失败计数放在进程内存里：{@code outbox_message} 表没有尝试次数列，加列会偏离
 * data-model/jobs.md 锁定的 schema。进程重启后计数归零，消息重新开始重试——
 * 这正是 outbox 想要的语义（不丢消息）。
 */
@Service
public class OutboxDispatchTick {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchTick.class);

    private final OutboxRepository outbox;
    private final JobMessageTransport transport;
    private final int batchSize;
    private final int maxAttempts;
    private final Map<UUID, Integer> failures = new ConcurrentHashMap<>();

    public OutboxDispatchTick(OutboxRepository outbox,
                              JobMessageTransport transport,
                              @Value("${flora.job.outbox.batch-size:50}") int batchSize,
                              @Value("${flora.job.outbox.max-attempts:10}") int maxAttempts) {
        this.outbox = outbox;
        this.transport = transport;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /** 扫一批 pending 消息投递，返回本轮成功投递的条数。 */
    public int tick() {
        int sent = 0;
        for (OutboxMessage message : outbox.findPending(batchSize)) {
            if (deliver(message)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean deliver(OutboxMessage message) {
        try {
            transport.publish(message);
            outbox.markSent(message.id());
            failures.remove(message.id());
            return true;
        } catch (RuntimeException e) {
            int attempts = failures.merge(message.id(), 1, Integer::sum);
            log.warn("outbox 投递失败 job={} event={} 连续失败 {} 次", message.aggregateId(), message.id(), attempts, e);
            if (attempts >= maxAttempts) {
                outbox.markFailed(message.id());
                failures.remove(message.id());
                log.error("outbox 连续投递失败到上限，标记 failed event={}", message.id());
            }
            return false;
        }
    }
}
