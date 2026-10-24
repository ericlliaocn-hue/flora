package com.flora.api.billing.application;

import java.util.UUID;

import com.flora.api.billing.domain.Wallet;
import org.springframework.stereotype.Service;

/**
 * 查当前用户的钱包快照。
 *
 * <p>读之前先把该用户「任务已终态但还没结算」的冻结单结算掉，
 * 这样客户端紧接着查 job 再查钱包时看到的余额已经是收敛后的（消除轮询窗口抖动）。
 */
@Service
public class GetWallet {

    private final WalletService wallets;
    private final WalletHoldSettlementTick settlement;

    public GetWallet(WalletService wallets, WalletHoldSettlementTick settlement) {
        this.wallets = wallets;
        this.settlement = settlement;
    }

    public WalletView get(UUID userId) {
        settlement.settleForUser(userId);
        Wallet wallet = wallets.ensureWallet(userId);
        return new WalletView(wallet.userId().toString(), wallet.available(), wallet.frozen(), wallet.updatedAt());
    }
}
