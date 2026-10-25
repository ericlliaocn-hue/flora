package com.pilipala.api.billing.infra;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "pilipala.billing.recharge")
public record RechargeProperties(
        @DefaultValue("30") int orderTtlMinutes,
        @DefaultValue("5000") long reconcilePollMs,
        @DefaultValue("50") int reconcileBatchSize,
        List<RechargePackageProperties> packages) {
}
