package art.pilipala.api.billing.application;

import art.pilipala.api.common.PilipalaPublicProperties;
import art.pilipala.api.billing.infra.ZpayProperties;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Component;

@Component
public class RechargeConfigValidator {

    private final PilipalaPublicProperties publicProperties;
    private final ZpayProperties zpay;

    public RechargeConfigValidator(PilipalaPublicProperties publicProperties, ZpayProperties zpay) {
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
