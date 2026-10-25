package com.pilipala.api.billing.infra;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.pilipala.api.billing.domain.Wallet;

@Repository
public class MybatisWalletRepository implements WalletRepository {

    private final WalletMapper mapper;

    public MybatisWalletRepository(WalletMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Wallet> findWallet(UUID walletId) {
        return Optional.ofNullable(mapper.findWallet(walletId));
    }

    @Override
    public Optional<Wallet> findByUserId(UUID userId) {
        return Optional.ofNullable(mapper.findByUserId(userId));
    }

    @Override
    public Optional<Wallet> insertIfAbsent(UUID userId) {
        return Optional.ofNullable(mapper.insertIfAbsent(userId));
    }

    @Override
    public boolean reserve(UUID walletId, long amount) {
        return mapper.reserve(walletId, amount) > 0;
    }

    @Override
    public boolean addAvailable(UUID walletId, long amount) {
        return mapper.addAvailable(walletId, amount) > 0;
    }

    @Override
    public boolean deductAvailable(UUID walletId, long amount) {
        return mapper.deductAvailable(walletId, amount) > 0;
    }

    @Override
    public boolean consumeFrozen(UUID walletId, long amount) {
        return mapper.consumeFrozen(walletId, amount) > 0;
    }

    @Override
    public boolean releaseFrozen(UUID walletId, long amount) {
        return mapper.releaseFrozen(walletId, amount) > 0;
    }
}
