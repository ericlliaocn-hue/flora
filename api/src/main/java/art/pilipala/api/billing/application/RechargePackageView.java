package art.pilipala.api.billing.application;

import art.pilipala.api.billing.infra.RechargePackageProperties;

public record RechargePackageView(String id, long moneyFen, long credits, String name) {

    public static RechargePackageView of(RechargePackageProperties pkg) {
        return new RechargePackageView(pkg.id(), pkg.moneyFen(), pkg.credits(), pkg.name());
    }
}
