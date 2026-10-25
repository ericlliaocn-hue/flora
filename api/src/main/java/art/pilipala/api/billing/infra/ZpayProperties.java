package art.pilipala.api.billing.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "pilipala.billing.zpay")
public record ZpayProperties(
        @DefaultValue("https://api.z-pay.cn") String apiBase,
        String pid,
        String pkey,
        @DefaultValue("12540") String cid) {

    public boolean isConfigured() {
        return pid != null && !pid.isBlank() && pkey != null && !pkey.isBlank();
    }

    public String normalizedApiBase() {
        String base = apiBase == null || apiBase.isBlank() ? "https://api.z-pay.cn" : apiBase.trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
