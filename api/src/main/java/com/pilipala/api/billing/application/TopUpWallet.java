package com.pilipala.api.billing.application;

import java.util.UUID;

import com.pilipala.api.billing.domain.Wallet;
import com.pilipala.api.billing.infra.BillingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 充值用例：加可用余额，并同时记一笔 {@code topup} 流水。
 *
 * <p>这是「往用户账上加钱」的唯一入口：注册赠送、运维脚本、未来 WP11 Admin 的
 * 受控加额都走这里，保证任何一笔进账都能在 {@code wallet_ledger} 里查到。
 *
 * <p>C 端渠道充值（WP16）在入账时也应经 {@link #topUpForRecharge}，保证流水带 {@code recharge_order_id}。
 */
@Service
public class TopUpWallet {

    private final WalletService wallets;
    private final BillingProperties properties;

    public TopUpWallet(WalletService wallets, BillingProperties properties) {
        this.wallets = wallets;
        this.properties = properties;
    }

    /**
     * 充值。金额必须为正整数。
     *
     * @param userId 收款用户
     * @param amount 充值的 credit 数
     */
    @Transactional
    public Wallet topUp(UUID userId, long amount) {
        return wallets.credit(userId, amount);
    }

    @Transactional
    public Wallet topUpForRecharge(UUID userId, long amount, UUID rechargeOrderId) {
        wallets.credit(userId, amount, rechargeOrderId);
        return wallets.ensureWallet(userId);
    }

    /**
     * 注册后初始化钱包：确保有钱包行；配置了初始额度就经充值路径记一笔流水。
     *
     * <p>{@code pilipala.billing.initial-credits > 0} 时绝不「只改 wallet.available」，
     * 一定同时留下 {@code type='topup'} 的流水。
     */
    @Transactional
    public Wallet ensureInitialWallet(UUID userId) {
        long credits = properties.initialCredits();
        if (credits <= 0) {
            return wallets.ensureWallet(userId);
        }
        return topUp(userId, credits);
    }
}
