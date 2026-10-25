package com.flora.api.billing.application;

import com.flora.api.common.FloraPublicProperties;
import com.flora.api.billing.infra.ZpayProperties;
import com.flora.api.job.infra.JobException;
import org.springframework.stereotype.Component;

@Component
public class RechargeConfigValidator {

    private final FloraPublicProperties publicProperties;
    private final ZpayProperties zpay;

    public RechargeConfigValidator(FloraPublicProperties publicProperties, ZpayProperties zpay) {
        this.publicProperties = publicProperties;
        this.zpay = zpay;
    }

    public void requireCreateOrderConfig() {
        if (!publicProperties.isPublicBaseUrlConfigured() || !zpay.isConfigured()) {
            throw JobException.validation();
        }
    }

    public String notifyUrl() {
        return publicProperties.normalizedPublicBaseUrl() + "/api/v1/billing/recharge/webhook/zpay";
    }

    public String returnUrl() {
        return publicProperties.normalizedPublicBaseUrl() + "/billing/recharge/result";
    }
}
