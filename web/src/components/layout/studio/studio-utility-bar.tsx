import { Tooltip } from "antd";
import { Bot, Building2, Coins, Menu, Star, Zap } from "lucide-react";
import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";

import { useAuthStore } from "@/stores/use-auth-store";

type StudioUtilityBarProps = {
    onOpenMenu: () => void;
    onToggleAgent: () => void;
    onRecharge: () => void;
    agentPanelOpen: boolean;
};

export function StudioUtilityBar({ onOpenMenu, onToggleAgent, onRecharge, agentPanelOpen }: StudioUtilityBarProps) {
    const { t } = useTranslation();
    const user = useAuthStore((state) => state.user);
    const wallet = useAuthStore((state) => state.wallet);
    const refreshWallet = useAuthStore((state) => state.refreshWallet);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);
    const logout = useAuthStore((state) => state.logout);
    const initialize = useAuthStore((state) => state.initialize);
    const startedRef = useRef(false);

    useEffect(() => {
        if (startedRef.current) return;
        startedRef.current = true;
        void initialize();
    }, [initialize]);

    const balance = wallet?.available ?? (user ? "—" : "100");

    return (
        <header className="sticky top-0 z-30 flex h-12 shrink-0 items-center justify-end gap-2 border-b border-white/[0.08] bg-[#0b0b0b]/92 px-4 backdrop-blur-xl">
            <button
                type="button"
                className="mr-auto inline-flex size-8 items-center justify-center rounded-md text-neutral-300 hover:bg-white/[0.06] md:hidden"
                onClick={onOpenMenu}
                aria-label={t("topNav.openMenu")}
            >
                <Menu className="size-5" />
            </button>

            <div className="flex items-center gap-2">
                <UtilityButton icon={Building2} label={t("studio.utilities.enterprise")} onClick={() => window.open(`mailto:${t("studio.enterpriseEmail")}`, "_self")} />
                <UtilityButton
                    icon={Coins}
                    label={t("studio.utilities.points")}
                    onClick={() => {
                        if (user) void refreshWallet();
                        else setAuthModalOpen(true, "login");
                    }}
                />
                <UtilityButton icon={Star} label={t("studio.utilities.recharge")} badge={t("studio.utilities.rechargeBadge")} onClick={onRecharge} />
                <button
                    type="button"
                    className="inline-flex h-8 min-w-[58px] items-center justify-center gap-1 rounded-lg border border-white/[0.14] bg-white/[0.02] px-3 text-xs font-semibold tabular-nums text-neutral-100"
                    onClick={() => (user ? void refreshWallet() : setAuthModalOpen(true, "login"))}
                    title={t("wallet.title")}
                >
                    <Zap className="size-3.5 text-neutral-300" aria-hidden="true" />
                    {balance}
                </button>

                {user ? (
                    <Tooltip title={t("auth.signedInAs", { username: user.username })}>
                        <button
                            type="button"
                            className="relative size-8 overflow-hidden rounded-full border border-white/[0.14] bg-gradient-to-br from-[#2a3340] to-[#1a1e26]"
                            onClick={() => void logout()}
                            aria-label={t("auth.logout")}
                        >
                            <span className="absolute inset-1 rounded-full bg-[radial-gradient(circle_at_40%_35%,#6a7a8c,#2a3038_70%)]" />
                        </button>
                    </Tooltip>
                ) : (
                    <button
                        type="button"
                        className="h-8 rounded-lg border border-white/[0.14] bg-white/[0.02] px-3 text-xs text-neutral-300 transition hover:bg-white/[0.05] hover:text-neutral-100"
                        onClick={() => setAuthModalOpen(true, "login")}
                    >
                        {t("auth.needLogin")}
                    </button>
                )}

                <Tooltip title={t(agentPanelOpen ? "topNav.closeAgent" : "topNav.openAgent")}>
                    <button
                        type="button"
                        className="inline-flex size-8 items-center justify-center rounded-lg border border-white/[0.14] bg-white/[0.02] text-neutral-300 transition hover:bg-white/[0.05] hover:text-neutral-100"
                        onClick={onToggleAgent}
                        aria-label={t(agentPanelOpen ? "topNav.closeAgent" : "topNav.openAgent")}
                    >
                        <Bot className="size-4" />
                    </button>
                </Tooltip>
            </div>
        </header>
    );
}

function UtilityButton({ icon: Icon, label, badge, onClick }: { icon: typeof Building2; label: string; badge?: string; onClick: () => void }) {
    return (
        <button
            type="button"
            className="relative inline-flex h-8 items-center gap-1.5 rounded-lg border border-white/[0.14] bg-white/[0.02] px-3 text-[12.5px] text-neutral-400 transition hover:border-white/20 hover:bg-white/[0.05] hover:text-neutral-100"
            onClick={onClick}
        >
            <Icon className="size-3.5" strokeWidth={1.6} />
            <span className="hidden sm:inline">{label}</span>
            {badge ? (
                <span className="pointer-events-none absolute -right-1.5 -top-1.5 rounded-full border border-white/15 bg-[#3d4a5c] px-1.5 text-[9.5px] font-semibold leading-4 text-[#e8eef6]">
                    {badge}
                </span>
            ) : null}
        </button>
    );
}
