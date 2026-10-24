package com.flora.api.billing.application;

import java.util.UUID;

import com.flora.api.billing.domain.Wallet;
import com.flora.api.billing.infra.BillingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 充值用例：加可用余额，并同时记一笔 {@code topup} 流水。
 *
 * <p>这是「往用户账上加钱」的唯一入口：注册赠送、运维脚本、未来 WP11 Admin 的
 * 受控加额都走这里，保证任何一笔进账都能在 {@code wallet_ledger} 里查到。
 *
 * <p>本 WP 不暴露 C 端自助充值接口（防生产越权刷钱）。
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

    /**
     * 注册后初始化钱包：确保有钱包行；配置了初始额度就经充值路径记一笔流水。
     *
     * <p>{@code flora.billing.initial-credits > 0} 时绝不「只改 wallet.available」，
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
