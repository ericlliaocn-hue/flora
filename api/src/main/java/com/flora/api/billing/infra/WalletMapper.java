package com.flora.api.billing.infra;

import java.util.UUID;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.flora.api.billing.domain.Wallet;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WalletMapper {

    String COLS = "id, user_id, available, frozen, updated_at";

    @Select("SELECT " + COLS + " FROM wallet WHERE id = #{walletId}")
    Wallet findWallet(@Param("walletId") UUID walletId);

    @Select("SELECT " + COLS + " FROM wallet WHERE user_id = #{userId}")
    Wallet findByUserId(@Param("userId") UUID userId);

    @Select("INSERT INTO wallet (user_id, available, frozen) VALUES (#{userId}, 0, 0) "
            + "ON CONFLICT (user_id) DO NOTHING RETURNING " + COLS)
    Wallet insertIfAbsent(@Param("userId") UUID userId);

    @Update("UPDATE wallet SET available = available - #{amount}, frozen = frozen + #{amount}, updated_at = now() "
            + "WHERE id = #{walletId} AND available >= #{amount}")
    int reserve(@Param("walletId") UUID walletId, @Param("amount") long amount);

    @Update("UPDATE wallet SET available = available + #{amount}, updated_at = now() WHERE id = #{walletId}")
    int addAvailable(@Param("walletId") UUID walletId, @Param("amount") long amount);

    @Update("UPDATE wallet SET frozen = frozen - #{amount}, updated_at = now() "
            + "WHERE id = #{walletId} AND frozen >= #{amount}")
    int consumeFrozen(@Param("walletId") UUID walletId, @Param("amount") long amount);

    @Update("UPDATE wallet SET available = available + #{amount}, frozen = frozen - #{amount}, updated_at = now() "
            + "WHERE id = #{walletId} AND frozen >= #{amount}")
    int releaseFrozen(@Param("walletId") UUID walletId, @Param("amount") long amount);
}
