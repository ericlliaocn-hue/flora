import { Tooltip } from "antd";
import { Bot, Building2, Coins, Menu, Star, Zap } from "lucide-react";
import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";

import { cn } from "@/lib/utils";
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
        <header className="sticky top-0 z-30 flex h-[52px] shrink-0 items-center justify-end gap-2.5 border-b border-white/[0.06] bg-[#0b0b0b]/95 px-4 backdrop-blur-xl md:px-5">
            <button
                type="button"
                className="mr-auto inline-flex size-9 items-center justify-center rounded-full text-neutral-300 hover:bg-white/[0.06] md:hidden"
                onClick={onOpenMenu}
                aria-label={t("topNav.openMenu")}
            >
                <Menu className="size-5" />
            </button>

            <div className="flex items-center gap-2 md:gap-2.5">
                <UtilityButton icon={Building2} label={t("studio.utilities.enterprise")} onClick={() => window.open(`mailto:${t("studio.enterpriseEmail")}`, "_self")} />
                <UtilityButton
                    icon={Coins}
                    label={t("studio.utilities.points")}
                    onClick={() => {
                        if (user) void refreshWallet();
                        else setAuthModalOpen(true, "login");
                    }}
                />
                <UtilityButton icon={Star} label={t("studio.utilities.recharge")} badge={t("studio.utilities.rechargeBadge")} accent onClick={onRecharge} />
                <button
                    type="button"
                    className="inline-flex h-9 min-w-[62px] items-center justify-center gap-1 rounded-full border border-white/[0.12] bg-white/[0.03] px-3.5 text-[12.5px] font-semibold tabular-nums text-neutral-100 transition hover:border-white/20 hover:bg-white/[0.06]"
                    onClick={() => (user ? void refreshWallet() : setAuthModalOpen(true, "login"))}
                    title={t("wallet.title")}
                >
                    <Zap className="size-3.5 text-[#7ec9c3]" aria-hidden="true" />
                    {balance}
                </button>

                {user ? (
                    <Tooltip title={t("auth.signedInAs", { username: user.username })}>
                        <button
                            type="button"
                            className="relative size-9 overflow-hidden rounded-full border border-white/[0.14] bg-gradient-to-br from-[#2a3340] to-[#1a1e26] transition hover:border-white/25"
                            onClick={() => void logout()}
                            aria-label={t("auth.logout")}
                        >
                            <span className="absolute inset-1 rounded-full bg-[radial-gradient(circle_at_40%_35%,#6a7a8c,#2a3038_70%)]" />
                        </button>
                    </Tooltip>
                ) : (
                    <button
                        type="button"
                        className="h-9 rounded-full border border-white/[0.12] bg-white/[0.03] px-4 text-[12.5px] text-neutral-200 transition hover:border-white/20 hover:bg-white/[0.06]"
                        onClick={() => setAuthModalOpen(true, "login")}
                    >
                        {t("auth.needLogin")}
                    </button>
                )}

                <Tooltip title={t(agentPanelOpen ? "topNav.closeAgent" : "topNav.openAgent")}>
                    <button
                        type="button"
                        className="inline-flex size-9 items-center justify-center rounded-full border border-white/[0.12] bg-white/[0.03] text-neutral-300 transition hover:border-white/20 hover:bg-white/[0.06] hover:text-neutral-100"
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

function UtilityButton({
    icon: Icon,
    label,
    badge,
    accent,
    onClick,
}: {
    icon: typeof Building2;
    label: string;
    badge?: string;
    accent?: boolean;
    onClick: () => void;
}) {
    return (
        <button
            type="button"
            className={cn(
                "relative inline-flex h-9 items-center gap-1.5 rounded-full border px-3.5 text-[12.5px] transition",
                accent
                    ? "border-[#3d9a94]/50 bg-[#0d3d3d]/60 text-[#d4f5f2] hover:border-[#5ec9c0]/60 hover:bg-[#0f4848]/80"
                    : "border-white/[0.12] bg-transparent text-neutral-400 hover:border-white/20 hover:bg-white/[0.05] hover:text-neutral-100",
            )}
            onClick={onClick}
        >
            <Icon className="size-3.5" strokeWidth={1.6} />
            <span className="hidden sm:inline">{label}</span>
            {badge ? (
                <span className="pointer-events-none absolute -right-1 -top-2 rounded-full border border-[#1a6b6b]/50 bg-[#1a8a84] px-1.5 text-[9px] font-semibold leading-4 text-white shadow-sm">
                    {badge}
                </span>
            ) : null}
        </button>
    );
}
