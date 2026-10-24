package com.flora.api.billing.infra;

import java.util.Optional;
import java.util.UUID;

import com.flora.api.billing.domain.Wallet;

/**
 * 钱包余额的存取契约。Postgres 是权威存储，实现在 {@link JdbcWalletRepository}。
 *
 * <p>定义成接口是为了让应用层用例能在没有数据库的情况下被测试。
 * 余额变动方法都带守卫条件：更新 0 行表示余额不足，返回 false，调用方负责回滚。
 */
public interface WalletRepository {

    Optional<Wallet> findWallet(UUID walletId);

    Optional<Wallet> findByUserId(UUID userId);

    /** 插入一个 0 余额的钱包；该用户已有钱包时不插入。返回新插入的钱包。 */
    Optional<Wallet> insertIfAbsent(UUID userId);

    /** 冻结：可用减、已冻加。{@code available < amount} 时不更新，返回 false。 */
    boolean reserve(UUID walletId, long amount);

    /** 充值：可用加。 */
    boolean addAvailable(UUID walletId, long amount);

    /** 确认实扣：已冻减。已冻不足时不更新，返回 false。 */
    boolean consumeFrozen(UUID walletId, long amount);

    /** 解冻退回：可用加、已冻减。已冻不足时不更新，返回 false。 */
    boolean releaseFrozen(UUID walletId, long amount);
}
