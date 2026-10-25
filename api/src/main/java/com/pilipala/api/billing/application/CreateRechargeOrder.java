package com.pilipala.api.billing.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.pilipala.api.billing.domain.RechargeOrder;
import com.pilipala.api.billing.domain.RechargeOrderStatus;
import com.pilipala.api.billing.infra.OutTradeNoGenerator;
import com.pilipala.api.billing.infra.RechargeOrderRepository;
import com.pilipala.api.billing.infra.RechargePackageProperties;
import com.pilipala.api.billing.infra.RechargeProperties;
import com.pilipala.api.billing.infra.ZpayClient;
import com.pilipala.api.billing.infra.ZpayClient.MapiRequest;
import com.pilipala.api.billing.infra.ZpayClient.MapiResult;
import com.pilipala.api.billing.infra.ZpayUrlNormalizer;
import com.pilipala.api.job.infra.JobException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pilipala.api.billing.infra.ClientIpResolver;

@Service
public class CreateRechargeOrder {

    private final RechargeConfigValidator config;
    private final RechargePackageCatalog catalog;
    private final RechargeProperties rechargeProperties;
    private final RechargeOrderRepository orders;
    private final ZpayClient zpay;
    private final com.pilipala.api.billing.infra.ZpayProperties zpayProperties;

    public CreateRechargeOrder(
            RechargeConfigValidator config,
            RechargePackageCatalog catalog,
            RechargeProperties rechargeProperties,
            RechargeOrderRepository orders,
            ZpayClient zpay,
            com.pilipala.api.billing.infra.ZpayProperties zpayProperties) {
        this.config = config;
        this.catalog = catalog;
        this.rechargeProperties = rechargeProperties;
        this.orders = orders;
        this.zpay = zpay;
        this.zpayProperties = zpayProperties;
    }

    @Transactional
    public RechargeOrderView create(UUID userId, String packageId, String device, HttpServletRequest request) {
        config.requireCreateOrderConfig();
        RechargePackageProperties pkg = catalog.require(packageId);
        boolean mobile = "mobile".equalsIgnoreCase(device);
        String clientIp = ClientIpResolver.resolve(request);
        String outTradeNo = OutTradeNoGenerator.generate();
        RechargeOrder created = orders.insertCreated(new RechargeOrder(
                null,
                userId,
                outTradeNo,
                "zpay",
                null,
                pkg.id(),
                pkg.moneyFen(),
                pkg.credits(),
                RechargeOrderStatus.CREATED,
                clientIp,
                mobile ? "mobile" : null,
                null,
                null,
                pkg.name(),
                null,
                null,
                null,
                null,
                null,
                null));
        try {
            MapiResult result = zpay.createAlipayOrder(new MapiRequest(
                    outTradeNo,
                    config.notifyUrl(),
                    config.returnUrl(),
                    pkg.name(),
                    pkg.moneyFen(),
                    clientIp,
                    mobile));
            String apiBase = zpayProperties.normalizedApiBase();
            String payUrl = ZpayUrlNormalizer.normalize(
                    mobile && result.payurl2() != null ? result.payurl2() : result.payurl(), apiBase);
            String qrcodeUrl = ZpayUrlNormalizer.normalize(result.qrcode(), apiBase);
            if (qrcodeUrl == null && payUrl != null) {
                qrcodeUrl = payUrl;
            }
            orders.markPendingPay(created.id(), result.tradeNo(), payUrl, qrcodeUrl);
            RechargeOrder pending = orders.findByOutTradeNo(outTradeNo).orElseThrow();
            Instant expireAt = pending.createdAt().plus(rechargeProperties.orderTtlMinutes(), ChronoUnit.MINUTES);
            return RechargeOrderView.of(pending, expireAt);
        } catch (JobException e) {
            orders.markFailed(created.id(), e.errorCode());
            throw e;
        } catch (RuntimeException e) {
            orders.markFailed(created.id(), "provider_error");
            throw JobException.rechargeProviderError();
        }
    }
}
