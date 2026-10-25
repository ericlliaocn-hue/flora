package art.pilipala.api.billing.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 账本流水：每次余额或冻结额变动都有一行，方向由 {@code type} 决定。
 *
 * <p>{@code amount} 恒为正整数（{@code CHECK (amount <> 0)}，代码只写 > 0），
 * 实现者不要靠正负号猜语义。
 *
 * @param id        主键
 * @param walletId  所属钱包
 * @param userId    冗余用户 id，便于对账
 * @param type      流水类型
 * @param amount    金额（正整数）
 * @param holdId    关联冻结单；充值时为 null
 * @param jobId            关联任务；充值时为 null
 * @param rechargeOrderId  关联充值订单；非渠道入账时为 null
 * @param createdAt        记账时间
 */
public record WalletLedgerEntry(
        UUID id,
        UUID walletId,
        UUID userId,
        WalletLedgerType type,
        long amount,
        UUID holdId,
        UUID jobId,
        UUID rechargeOrderId,
        Instant createdAt) {
}
