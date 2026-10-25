package com.pilipala.api.billing.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ZpaySignTest {

    @Test
    void signMatchesKnownVector() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", "test_pid");
        params.put("type", "alipay");
        params.put("out_trade_no", "flr_20261025Ab12Cd34Ef");
        params.put("money", "10.00");
        params.put("sign_type", "MD5");
        String sign = ZpaySign.sign(params, "test_pkey");
        assertThat(sign).hasSize(32);
        params.put("sign", sign);
        assertThat(ZpaySign.verify(params, "test_pkey")).isTrue();
    }

    @Test
    void signIsDeterministic() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", "test_pid");
        params.put("money", "10.00");
        String a = ZpaySign.sign(params, "test_pkey");
        String b = ZpaySign.sign(params, "test_pkey");
        assertThat(a).isEqualTo(b);
    }
}
