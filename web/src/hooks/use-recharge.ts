import { App } from "antd";
import { useTranslation } from "react-i18next";

import { createRechargeOrder, fetchRechargePackages } from "@/services/api/pilipala-recharge";
import { useAuthStore } from "@/stores/use-auth-store";

export function useRecharge() {
    const { t } = useTranslation();
    const { message } = App.useApp();
    const user = useAuthStore((state) => state.user);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);

    return async () => {
        if (!user) {
            setAuthModalOpen(true, "login");
            return;
        }
        try {
            const packages = await fetchRechargePackages();
            if (!packages.length) {
                message.info(t("studio.recharge.unavailable"));
                return;
            }
            const order = await createRechargeOrder(packages[0].id);
            if (order.pay_url) {
                window.open(order.pay_url, "_blank", "noopener,noreferrer");
            } else {
                message.info(t("studio.recharge.unavailable"));
            }
        } catch (error) {
            message.error(error instanceof Error ? error.message : t("studio.recharge.failed"));
        }
    };
}
