package art.pilipala.api.job.infra;

import java.util.UUID;

/**
 * MQ 消息体：只有 {@code job_id} 与传输层 {@code event_id}（= outbox id）。
 *
 * <p>ADR 0003：禁止塞 params / 图片 / 钱包额。传输实现只负责把这段 JSON 送出去。
 */
public final class JobMessagePayload {

    private JobMessagePayload() {
    }

    public static String of(UUID jobId, UUID eventId) {
        return "{\"job_id\":\"" + jobId + "\",\"event_id\":\"" + eventId + "\"}";
    }
}
