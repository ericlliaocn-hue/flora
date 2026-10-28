package art.pilipala.api.job.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * job 行。列与 docs/engineering/data-model/jobs.md 一致。
 *
 * <p>禁止出现的字段（queue_name / routing_key / amqp_* / 上游 raw）不在这里，
 * 也不在表里：job 模型与队列实现解耦（ADR 0003）。
 *
 * @param id                主键，也是 MQ 消息体里的 job_id
 * @param userId            提交者
 * @param status            状态机权威
 * @param capability        如 image.generate
 * @param model             pilipala 内部模型名；WP21 起可为产品 code
 * @param productId         WP21 产品 SKU；老 job 为空
 * @param targetId          WP21 当前 / 最后 target
 * @param quotedPrice       WP21 冻结时的产品价快照（credits）
 * @param pricingVersion    WP21 定价版本
 * @param paramsJson        已物化参数（JSON 文本）
 * @param attempt           已开始的 attempt 数
 * @param maxAttempts       重试上限
 * @param holdId            钱包冻结单；WP6 之前恒为 null
 * @param idempotencyKey    客户端幂等键（与 userId 一起唯一）
 * @param cancelRequestedAt 取消意图
 * @param errorCode         映射后的稳定码
 * @param resultObjectKeysJson 成功时的对象键列表（JSON 文本）
 */
public record Job(
        UUID id,
        UUID userId,
        JobStatus status,
        String capability,
        String model,
        UUID productId,
        UUID targetId,
        Long quotedPrice,
        String pricingVersion,
        String paramsJson,
        int attempt,
        int maxAttempts,
        UUID holdId,
        String idempotencyKey,
        Instant cancelRequestedAt,
        String errorCode,
        String resultObjectKeysJson,
        Instant createdAt,
        Instant updatedAt) {
}
