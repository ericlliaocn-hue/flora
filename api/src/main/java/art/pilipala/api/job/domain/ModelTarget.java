package art.pilipala.api.job.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 视频路由的一个 target（上游渠道/分组）。表 {@code model_target}。
 *
 * <p>不是用户价：{@code costAmount} 是上游成本，只做毛利分析；用户价在 {@link ModelProduct}。
 *
 * @param constraintsJson 时长/比例/分辨率/参考素材上限（JSON 文本）
 */
public record ModelTarget(
        UUID id,
        String code,
        String gateway,
        String upstreamModel,
        String constraintsJson,
        String costType,
        BigDecimal costAmount,
        String costCurrency,
        int timeoutSeconds,
        boolean enabled) {
}
