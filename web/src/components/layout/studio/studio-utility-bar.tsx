import { Tooltip } from "antd";
import { Bot, Building2, Coins, Menu, Star, Zap } from "lucide-react";
import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";

import { AccountMenuPopover, WalletBalancePopover, shouldShowCreditsPill, walletAvailableDisplay } from "@/components/layout/auth-session-menus";
import { useStudioFeatures } from "@/components/layout/studio/use-studio-features";
import { shouldShowMembershipBadge } from "@/services/api/studio-features";
import { formatAuthIdentity, useAuthStore } from "@/stores/use-auth-store";

type StudioUtilityBarProps = {
    onOpenMenu: () => void;
    onToggleAgent: () => void;
    onRecharge: () => void;
    agentPanelOpen: boolean;
};

export function StudioUtilityBar({ onOpenMenu, onToggleAgent, onRecharge, agentPanelOpen }: StudioUtilityBarProps) {
    const { t } = useTranslation();
    const features = useStudioFeatures();
    const user = useAuthStore((state) => state.user);
    const wallet = useAuthStore((state) => state.wallet);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);
    const initialize = useAuthStore((state) => state.initialize);
    const startedRef = useRef(false);

    useEffect(() => {
        if (startedRef.current) return;
        startedRef.current = true;
        void initialize();
    }, [initialize]);

    return (
        <header className="sticky top-0 z-30 flex h-[48px] shrink-0 items-center justify-end gap-2 border-b border-white/[0.06] bg-[#0b0b0b] px-3 md:px-4">
            <button
                type="button"
                className="mr-auto inline-flex size-9 items-center justify-center rounded-full text-neutral-400 hover:bg-white/[0.05] hover:text-neutral-200 md:hidden"
                onClick={onOpenMenu}
                aria-label={t("topNav.openMenu")}
            >
                <Menu className="size-5" />
            </button>

            <div className="flex items-center gap-1.5 md:gap-2">
                <QuietLink icon={Building2} label={t("studio.utilities.enterprise")} onClick={() => window.open(`mailto:${t("studio.enterpriseEmail")}`, "_self")} />
                {user ? (
                    <WalletBalancePopover>
                        <QuietLink icon={Coins} label={t("studio.utilities.points")} />
                    </WalletBalancePopover>
                ) : (
                    <QuietLink icon={Coins} label={t("studio.utilities.points")} onClick={() => setAuthModalOpen(true, "login")} />
                )}
                {features.membership_enabled ? (
                    <button
                        type="button"
                        className="inline-flex h-8 items-center gap-1.5 rounded-full border border-white/[0.1] px-3 text-[12px] text-neutral-300 transition hover:border-white/[0.16] hover:text-neutral-100"
                        onClick={onRecharge}
                    >
                        <Star className="size-3.5" strokeWidth={1.6} />
                        <span>{t("studio.utilities.membership")}</span>
                        {shouldShowMembershipBadge(features.membership_badge) ? (
                            <span className="rounded-sm bg-[#1a8a84] px-1 text-[9px] font-semibold leading-4 text-white">
                                {features.membership_badge}
                            </span>
                        ) : null}
                    </button>
                ) : null}
                {shouldShowCreditsPill(user) ? (
                    <WalletBalancePopover>
                        <button
                            type="button"
                            className="inline-flex h-8 min-w-[52px] items-center justify-center gap-1 rounded-full border border-white/[0.08] bg-white/[0.02] px-2.5 text-[12px] font-medium tabular-nums text-neutral-200 transition hover:border-white/[0.14] hover:bg-white/[0.04]"
                            title={t("wallet.title")}
                        >
                            <Zap className="size-3.5 text-[#6eb8b2]" aria-hidden="true" />
                            {walletAvailableDisplay(wallet)}
                        </button>
                    </WalletBalancePopover>
                ) : null}

                {user ? (
                    <AccountMenuPopover>
                        <button
                            type="button"
                            className="relative size-8 overflow-hidden rounded-full border border-white/[0.12] bg-gradient-to-br from-[#2a3340] to-[#1a1e26] transition hover:border-white/20"
                            aria-label={t("auth.accountMenu")}
                        >
                            <span className="absolute inset-1 rounded-full bg-[radial-gradient(circle_at_40%_35%,#6a7a8c,#2a3038_70%)]" />
                            <span className="sr-only">{formatAuthIdentity(user)}</span>
                        </button>
                    </AccountMenuPopover>
                ) : (
                    <button
                        type="button"
                        className="studio-solid-on-white h-8 rounded-full bg-white px-4 text-[12px] font-semibold transition hover:bg-neutral-100"
                        onClick={() => setAuthModalOpen(true, "login")}
                    >
                        {t("auth.needLogin")}
                    </button>
                )}

                <Tooltip title={t(agentPanelOpen ? "topNav.closeAgent" : "topNav.openAgent")}>
                    <button
                        type="button"
                        className="inline-flex size-8 items-center justify-center rounded-full text-neutral-500 transition hover:bg-white/[0.05] hover:text-neutral-200"
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

function QuietLink({ icon: Icon, label, onClick }: { icon: typeof Building2; label: string; onClick?: () => void }) {
    return (
        <button
            type="button"
            className="inline-flex h-8 items-center gap-1.5 rounded-full border border-white/[0.12] px-2.5 text-[12px] text-neutral-300 transition hover:border-white/[0.18] hover:bg-white/[0.04] hover:text-neutral-100"
            onClick={onClick}
        >
            <Icon className="size-3.5" strokeWidth={1.6} />
            <span>{label}</span>
        </button>
    );
}
