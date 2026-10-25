package com.pilipala.api.billing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.pilipala.api.billing.domain.RechargeOrder;
import com.pilipala.api.billing.domain.RechargeOrderStatus;
import com.pilipala.api.billing.domain.WalletLedgerType;
import com.pilipala.api.billing.infra.RechargeOrderRepository;
import com.pilipala.api.billing.support.InMemoryWalletStore;
import org.junit.jupiter.api.Test;

class FinalizeRechargePaymentTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();

    @Test
    void idempotentPaidDoesNotDoubleTopup() {
        InMemoryWalletStore store = new InMemoryWalletStore();
        WalletService wallets = new WalletService(store, store, store);
        StubOrders orders = new StubOrders(paidOrder());
        FinalizeRechargePayment finalize = new FinalizeRechargePayment(orders, wallets);

        assertThat(finalize.applyPaid("flr_1", "t1", 1000)).isFalse();
        assertThat(store.findLedgerByUserId(USER)).isEmpty();
    }

    @Test
    void creditsOnceForPendingPay() {
        InMemoryWalletStore store = new InMemoryWalletStore();
        WalletService wallets = new WalletService(store, store, store);
        StubOrders orders = new StubOrders(pendingOrder());
        FinalizeRechargePayment finalize = new FinalizeRechargePayment(orders, wallets);

        assertThat(finalize.applyPaid("flr_1", "t1", 1000)).isTrue();
        assertThat(store.walletOf(USER).available()).isEqualTo(100);
        assertThat(store.findLedgerByUserId(USER).get(0).type()).isEqualTo(WalletLedgerType.TOPUP);
        assertThat(store.findLedgerByUserId(USER).get(0).rechargeOrderId()).isEqualTo(ORDER_ID);
        assertThat(finalize.applyPaid("flr_1", "t1", 1000)).isFalse();
        assertThat(store.walletOf(USER).available()).isEqualTo(100);
    }

    private static RechargeOrder pendingOrder() {
        return new RechargeOrder(
                ORDER_ID, USER, "flr_1", "zpay", null, "p10", 1000, 100, RechargeOrderStatus.PENDING_PAY,
                "127.0.0.1", null, null, null, "n", null, null, null, null, Instant.now(), Instant.now());
    }

    private static RechargeOrder paidOrder() {
        return new RechargeOrder(
                ORDER_ID, USER, "flr_1", "zpay", "t1", "p10", 1000, 100, RechargeOrderStatus.PAID,
                "127.0.0.1", null, null, null, "n", UUID.randomUUID(), Instant.now(), null, null,
                Instant.now(), Instant.now());
    }

    private static final class StubOrders implements RechargeOrderRepository {
        private RechargeOrder order;

        StubOrders(RechargeOrder order) {
            this.order = order;
        }

        @Override
        public RechargeOrder insertCreated(RechargeOrder draft) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RechargeOrder> findByOutTradeNo(String outTradeNo) {
            return Optional.of(order);
        }

        @Override
        public Optional<RechargeOrder> findById(UUID id) {
            return Optional.of(order);
        }

        @Override
        public boolean markPendingPay(UUID id, String providerTradeNo, String payUrl, String qrcodeUrl) {
            return false;
        }

        @Override
        public boolean markFailed(UUID id, String reason) {
            return false;
        }

        @Override
        public boolean claimPaidIfPending(String outTradeNo, String providerTradeNo) {
            if (order.status() == RechargeOrderStatus.PAID) {
                return false;
            }
            order = new RechargeOrder(order.id(), order.userId(), order.outTradeNo(), order.provider(),
                    providerTradeNo, order.packageId(), order.moneyFen(), order.credits(), RechargeOrderStatus.PAID,
                    order.clientIp(), order.device(), order.payUrl(), order.qrcodeUrl(), order.productName(),
                    order.ledgerId(), Instant.now(), order.refundedAt(), order.failReason(), order.createdAt(),
                    Instant.now());
            return true;
        }

        @Override
        public boolean attachLedger(String outTradeNo, UUID ledgerId) {
            order = new RechargeOrder(order.id(), order.userId(), order.outTradeNo(), order.provider(),
                    order.providerTradeNo(), order.packageId(), order.moneyFen(), order.credits(), order.status(),
                    order.clientIp(), order.device(), order.payUrl(), order.qrcodeUrl(), order.productName(),
                    ledgerId, order.paidAt(), order.refundedAt(), order.failReason(), order.createdAt(),
                    Instant.now());
            return true;
        }

        @Override
        public boolean markClosed(UUID id) {
            return false;
        }

        @Override
        public boolean markRefunded(UUID id) {
            return false;
        }

        @Override
        public java.util.List<RechargeOrder> findPendingPayBefore(java.time.Instant before, int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<RechargeOrder> findAdmin(UUID userId, String status, String outTradeNo, int limit) {
            return java.util.List.of();
        }
    }
}
