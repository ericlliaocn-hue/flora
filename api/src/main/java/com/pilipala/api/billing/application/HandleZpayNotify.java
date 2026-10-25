package com.pilipala.api.billing.application;

import java.util.Map;
import java.util.Set;

import com.pilipala.api.billing.infra.ZpayMoney;
import com.pilipala.api.billing.infra.ZpayProperties;
import com.pilipala.api.billing.infra.ZpaySign;
import com.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;

@Service
public class HandleZpayNotify {

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "pid", "trade_no", "out_trade_no", "type", "name", "money", "trade_status", "param", "sign", "sign_type");

    private final ZpayProperties zpay;
    private final FinalizeRechargePayment finalizePayment;

    public HandleZpayNotify(ZpayProperties zpay, FinalizeRechargePayment finalizePayment) {
        this.zpay = zpay;
        this.finalizePayment = finalizePayment;
    }

    /**
     * 验签并入账。失败抛 {@link JobException} 或 {@link IllegalArgumentException} 供 webhook 返回 400。
     */
    public void handle(Map<String, String> params) {
        if (!zpay.isConfigured()) {
            throw JobException.validation();
        }
        if (!ALLOWED_KEYS.containsAll(params.keySet())) {
            throw new IllegalArgumentException("extra notify fields");
        }
        if (!ZpaySign.verify(params, zpay.pkey())) {
            throw new IllegalArgumentException("bad sign");
        }
        if (!"TRADE_SUCCESS".equals(params.get("trade_status"))) {
            throw new IllegalArgumentException("not success");
        }
        String outTradeNo = params.get("out_trade_no");
        long moneyFen = ZpayMoney.yuanStringToFen(params.get("money"));
        finalizePayment.applyPaid(outTradeNo, params.get("trade_no"), moneyFen);
    }
}
