package com.pilipala.api.billing.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import com.pilipala.api.billing.infra.ZpayProperties;
import com.pilipala.api.billing.infra.ZpaySign;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleZpayNotifyTest {

    @Mock
    private ZpayProperties zpay;

    @Mock
    private FinalizeRechargePayment finalizePayment;

    @InjectMocks
    private HandleZpayNotify handler;

    @Test
    void rejectsExtraFields() {
        when(zpay.isConfigured()).thenReturn(true);
        Map<String, String> params = baseNotify();
        params.put("evil", "1");
        assertThatThrownBy(() -> handler.handle(params)).isInstanceOf(IllegalArgumentException.class);
        verify(finalizePayment, never()).applyPaid(eq("flr_x"), eq("t1"), anyLong());
    }

    @Test
    void acceptsValidNotifyAndDelegates() {
        when(zpay.isConfigured()).thenReturn(true);
        when(zpay.pkey()).thenReturn("test_pkey");
        Map<String, String> params = signedNotify("10.00");
        handler.handle(params);
        verify(finalizePayment).applyPaid("flr_x", "t1", 1000);
    }

    private Map<String, String> baseNotify() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", "test_pid");
        params.put("trade_no", "t1");
        params.put("out_trade_no", "flr_x");
        params.put("type", "alipay");
        params.put("name", "n");
        params.put("money", "10.00");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("param", "");
        params.put("sign_type", "MD5");
        return params;
    }

    private Map<String, String> signedNotify(String money) {
        Map<String, String> params = baseNotify();
        params.put("money", money);
        params.put("sign", ZpaySign.sign(params, "test_pkey"));
        return params;
    }
}
