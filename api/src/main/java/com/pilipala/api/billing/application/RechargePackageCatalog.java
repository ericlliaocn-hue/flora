package com.pilipala.api.billing.application;

import com.pilipala.api.billing.infra.RechargePackageProperties;
import com.pilipala.api.billing.infra.RechargeProperties;
import com.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Component;

@Component
public class RechargePackageCatalog {

    private final RechargeProperties properties;

    public RechargePackageCatalog(RechargeProperties properties) {
        this.properties = properties;
    }

    public RechargePackageProperties require(String packageId) {
        if (packageId == null || packageId.isBlank() || properties.packages() == null) {
            throw JobException.validation();
        }
        return properties.packages().stream()
                .filter(pkg -> pkg.id().equals(packageId))
                .findFirst()
                .orElseThrow(JobException::validation);
    }
}
