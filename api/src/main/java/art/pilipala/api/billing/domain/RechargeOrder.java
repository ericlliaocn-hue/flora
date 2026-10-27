package art.pilipala.api.billing.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 充值订单（z-pay 等渠道）。
 */
public record RechargeOrder(
        UUID id,
        UUID userId,
        String outTradeNo,
        String provider,
        String providerTradeNo,
        String packageId,
        long moneyFen,
        long credits,
        RechargeOrderStatus status,
        String clientIp,
        String device,
        String payUrl,
        String qrcodeUrl,
        String productName,
        Long productId,
        String productCode,
        long bonusCredits,
        UUID ledgerId,
        Instant paidAt,
        Instant refundedAt,
        String failReason,
        RechargeOrderKind orderKind,
        Long planPriceId,
        Instant createdAt,
        Instant updatedAt) {
}
