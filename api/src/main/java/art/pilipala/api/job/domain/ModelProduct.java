package art.pilipala.api.job.domain;

import java.util.UUID;

/**
 * 视频产品（用户可下单的 SKU）。表 {@code model_product}。
 *
 * <p>价格只在这里，是用户价；上游成本在 {@link ModelTarget}，只做毛利分析。
 *
 * @param paramSchemaJson canonical 参数约束（JSON 文本）
 */
public record ModelProduct(
        UUID id,
        String code,
        String capability,
        String displayName,
        String description,
        long priceCredits,
        String paramSchemaJson,
        boolean enabled,
        int sortOrder) {
}
