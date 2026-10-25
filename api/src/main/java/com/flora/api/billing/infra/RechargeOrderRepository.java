package com.flora.api.billing.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.flora.api.billing.domain.RechargeOrder;

public interface RechargeOrderRepository {

    RechargeOrder insertCreated(RechargeOrder draft);

    Optional<RechargeOrder> findByOutTradeNo(String outTradeNo);

    Optional<RechargeOrder> findById(UUID id);

    boolean markPendingPay(UUID id, String providerTradeNo, String payUrl, String qrcodeUrl);

    boolean markFailed(UUID id, String reason);

    boolean claimPaidIfPending(String outTradeNo, String providerTradeNo);

    boolean attachLedger(String outTradeNo, UUID ledgerId);

    boolean markClosed(UUID id);

    boolean markRefunded(UUID id);

    List<RechargeOrder> findPendingPayBefore(Instant before, int limit);

    List<RechargeOrder> findAdmin(UUID userId, String status, String outTradeNo, int limit);
}
