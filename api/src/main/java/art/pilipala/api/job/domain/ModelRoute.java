package art.pilipala.api.job.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 产品到 target 的 fallback 链。表 {@code model_route}。
 *
 * <p>{@code priority} 小的先走；{@code maxCostAmount} 是该跳允许的上游成本上限。
 */
public record ModelRoute(
        UUID id,
        UUID productId,
        UUID targetId,
        int priority,
        BigDecimal maxCostAmount,
        boolean enabled) {
}
