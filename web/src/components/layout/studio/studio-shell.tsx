import type { ReactNode } from "react";
import { useEffect, useRef, useState } from "react";
import { useLocation } from "react-router-dom";

import { AppConfigModal } from "@/components/layout/app-config-modal";
import { StudioMobileDrawer } from "@/components/layout/studio/studio-mobile-drawer";
import { StudioPromoStrip } from "@/components/layout/studio/studio-promo-strip";
import { StudioSidebar } from "@/components/layout/studio/studio-sidebar";
import { StudioUtilityBar } from "@/components/layout/studio/studio-utility-bar";
import { App } from "antd";
import { useTranslation } from "react-i18next";

import { CanvasPluginManagerModal } from "@/components/canvas/canvas-plugin-manager-modal";
import { createRechargeOrder, fetchRechargePackages } from "@/services/api/flora-recharge";
import { useAuthStore } from "@/stores/use-auth-store";
import { useAgentStore } from "@/stores/use-agent-store";

type StudioShellProps = {
    children: ReactNode;
};

export function StudioShell({ children }: StudioShellProps) {
    const { t } = useTranslation();
    const { message } = App.useApp();
    const { pathname } = useLocation();
    const hideChrome = /^\/canvas\/[^/]+/.test(pathname);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);
    const user = useAuthStore((state) => state.user);
    const [mobileNavOpen, setMobileNavOpen] = useState(false);
    const [pluginsOpen, setPluginsOpen] = useState(false);
    const autoConnectRef = useRef(false);

    const agentToken = useAgentStore((state) => state.token);
    const agentEnabled = useAgentStore((state) => state.enabled);
    const agentConnected = useAgentStore((state) => state.connected);
    const connectAgent = useAgentStore((state) => state.connectAgent);
    const togglePanel = useAgentStore((state) => state.togglePanel);
    const panelOpen = useAgentStore((state) => state.panelOpen);

    useEffect(() => {
        if (autoConnectRef.current || agentEnabled || agentConnected || !agentToken.trim()) return;
        autoConnectRef.current = true;
        connectAgent({ silent: true });
    }, [agentConnected, agentEnabled, agentToken, connectAgent]);

    const handleRecharge = async () => {
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

    if (hideChrome) {
        return (
            <>
                <div className="min-h-0 flex-1 overflow-hidden">{children}</div>
                <AppConfigModal />
                <CanvasPluginManagerModal open={pluginsOpen} onClose={() => setPluginsOpen(false)} />
            </>
        );
    }

    return (
        <>
            <div className="flex min-h-0 flex-1 flex-col overflow-hidden bg-[#0b0b0b] text-neutral-100">
                <StudioPromoStrip onRecharge={() => void handleRecharge()} />
                <div className="flex min-h-0 flex-1">
                    <StudioSidebar onAgent={togglePanel} onPlugins={() => setPluginsOpen(true)} />
                    <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
                        <StudioUtilityBar
                            onOpenMenu={() => setMobileNavOpen(true)}
                            onToggleAgent={togglePanel}
                            onRecharge={() => void handleRecharge()}
                            agentPanelOpen={panelOpen}
                        />
                        <div className="min-h-0 flex-1 overflow-hidden">{children}</div>
                    </div>
                </div>
            </div>
            <StudioMobileDrawer
                open={mobileNavOpen}
                onClose={() => setMobileNavOpen(false)}
                onAgent={togglePanel}
                onPlugins={() => {
                    setMobileNavOpen(false);
                    setPluginsOpen(true);
                }}
            />
            <AppConfigModal />
            <CanvasPluginManagerModal open={pluginsOpen} onClose={() => setPluginsOpen(false)} />
        </>
    );
}
