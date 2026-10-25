package com.pilipala.api.admin.application;

import java.util.UUID;

import com.pilipala.api.auth.infra.UserRepository;
import com.pilipala.api.billing.application.TopUpWallet;
import com.pilipala.api.billing.application.WalletView;
import com.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用例：运维手工充值。
 *
 * <p>单账本红线（ADR 0001）：<b>只</b>走 {@link TopUpWallet#topUp}，
 * 由它在同一事务里加余额并写一条 {@code type='topup'} 的流水。
 * 严禁绕过它直接 {@code UPDATE wallet} —— 只改余额不写流水会让账本对不上。
 */
@Service
public class TopUpUserWallet {

    private final UserRepository users;
    private final TopUpWallet topUpWallet;

    public TopUpUserWallet(UserRepository users, TopUpWallet topUpWallet) {
        this.users = users;
        this.topUpWallet = topUpWallet;
    }

    /**
     * 充值。金额必须为正；用户不存在按参数不合法处理。
     *
     * @param userId 收款用户
     * @param amount 充值的 credit 数，必须大于 0
     */
    @Transactional
    public WalletView topUp(UUID userId, long amount) {
        if (amount <= 0) {
            throw JobException.validation();
        }
        if (users.findById(userId).isEmpty()) {
            throw JobException.validation();
        }
        var wallet = topUpWallet.topUp(userId, amount);
        return new WalletView(wallet.userId().toString(), wallet.available(), wallet.frozen(), wallet.updatedAt());
    }
}
