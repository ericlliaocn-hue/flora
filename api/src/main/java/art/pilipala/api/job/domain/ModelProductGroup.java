package art.pilipala.api.job.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 产品到 NewAPI group/model 的只读映射。channel 选择、retry、fallback 在 NewAPI。
 *
 * @param groupCode  NewAPI 分组 code；pilipala 不镜像 channel
 * @param modelCode  NewAPI 请求使用的 model
 * @param timeoutSeconds 该 group 的 task deadline 秒数
 */
public record ModelProductGroup(
        UUID id,
        UUID productId,
        String groupCode,
        String modelCode,
        int priority,
        int timeoutSeconds,
        BigDecimal maxCostAmount,
        boolean enabled) {
}
