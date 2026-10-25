package com.flora.api.billing.application;

import java.util.List;

import com.flora.api.billing.infra.RechargeProperties;
import org.springframework.stereotype.Service;

@Service
public class ListRechargePackages {

    private final RechargeProperties properties;

    public ListRechargePackages(RechargeProperties properties) {
        this.properties = properties;
    }

    public List<RechargePackageView> list() {
        if (properties.packages() == null) {
            return List.of();
        }
        return properties.packages().stream().map(RechargePackageView::of).toList();
    }
}
