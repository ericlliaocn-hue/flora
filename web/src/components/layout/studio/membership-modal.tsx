import { App } from "antd";
import { ChevronRight, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import {
    checkoutMembership,
    fetchMembershipMe,
    fetchMembershipPlans,
    type MembershipPlan,
    type MembershipPlanPrice,
} from "@/services/api/pilipala-membership";
import { useAuthStore } from "@/stores/use-auth-store";

const CORAL = "#E8867A";
const CYCLES = ["year", "quarter", "month"] as const;
type Cycle = (typeof CYCLES)[number];

type MembershipModalProps = {
    open: boolean;
    onClose: () => void;
};

export function useMembershipModal() {
    const [open, setOpen] = useState(false);
    return { open, openMembership: () => setOpen(true), closeMembership: () => setOpen(false) };
}

export function MembershipModal({ open, onClose }: MembershipModalProps) {
    const { t, i18n } = useTranslation();
    const { message } = App.useApp();
    const user = useAuthStore((state) => state.user);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);
    const [loading, setLoading] = useState(false);
    const [plans, setPlans] = useState<MembershipPlan[]>([]);
    const [cycle, setCycle] = useState<Cycle>("year");
    const [audience, setAudience] = useState<"personal" | "team">("personal");
    const [variants, setVariants] = useState<Record<string, string>>({});

    const localeZh = i18n.language.startsWith("zh");

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setPlans(await fetchMembershipPlans());
            if (user) {
                await fetchMembershipMe().catch(() => null);
            }
        } catch {
            message.error(t("membership.loadFailed"));
        } finally {
            setLoading(false);
        }
    }, [message, t, user]);

    useEffect(() => {
        if (open) void load();
    }, [open, load]);

    const cycleBadge = useMemo(() => {
        const badges = new Map<string, string>();
        for (const plan of plans) {
            for (const p of plan.prices) {
                if (p.badge_zh && !badges.has(p.cycle)) badges.set(p.cycle, p.badge_zh);
            }
        }
        return badges;
    }, [plans]);

    const cards = useMemo(() => {
        return plans
            .filter((p) => p.audience === audience)
            .map((plan) => {
                const prices = plan.prices.filter((p) => p.cycle === cycle);
                const variantCode = variants[plan.code] ?? prices[0]?.variant_code ?? "default";
                const price = prices.find((p) => p.variant_code === variantCode) ?? prices[0];
                return { plan, price, prices };
            })
            .filter((row) => row.price);
    }, [plans, cycle, audience, variants]);

    const checkout = async (price: MembershipPlanPrice) => {
        if (!user) {
            setAuthModalOpen(true, "login");
            return;
        }
        try {
            const order = await checkoutMembership(price.id);
            if (order.pay_url) {
                window.open(order.pay_url, "_blank", "noopener,noreferrer");
            } else {
                message.info(t("studio.recharge.unavailable"));
            }
        } catch (error) {
            message.error(error instanceof Error ? error.message : t("membership.checkoutFailed"));
        }
    };

    if (!open) return null;

    return (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-3 sm:p-6">
            <button type="button" className="absolute inset-0 bg-black/70 backdrop-blur-sm" aria-label={t("common.close")} onClick={onClose} />
            <div
                className="relative z-[1] flex max-h-[92vh] w-full max-w-[1180px] flex-col overflow-hidden rounded-2xl border border-white/10 bg-[#121212]/95 shadow-2xl"
                role="dialog"
                aria-modal="true"
            >
                <button
                    type="button"
                    className="absolute right-3 top-3 z-10 inline-flex size-8 items-center justify-center rounded-full text-neutral-400 hover:bg-white/10 hover:text-neutral-100"
                    onClick={onClose}
                >
                    <X className="size-4" />
                </button>

                <div className="border-b border-white/[0.06] px-5 pb-3 pt-5 sm:px-8">
                    <p className="text-center text-[13px] text-neutral-400">{t("membership.promoLine")}</p>
                    <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                        <div className="flex items-center gap-2 text-[12px] text-neutral-400">
                            <button
                                type="button"
                                className={`rounded-full px-3 py-1 ${audience === "personal" ? "bg-white/10 text-neutral-100" : "hover:bg-white/5"}`}
                                onClick={() => setAudience("personal")}
                            >
                                {t("membership.personal")}
                            </button>
                            <button
                                type="button"
                                className={`rounded-full px-3 py-1 ${audience === "team" ? "bg-white/10 text-neutral-100" : "hover:bg-white/5"}`}
                                onClick={() => setAudience("team")}
                            >
                                {t("membership.team")}
                            </button>
                        </div>
                        <div className="mx-auto flex rounded-full border border-white/10 bg-black/30 p-0.5">
                            {CYCLES.map((c) => (
                                <button
                                    key={c}
                                    type="button"
                                    className={`relative rounded-full px-4 py-1.5 text-[12px] transition ${
                                        cycle === c ? "bg-white text-black" : "text-neutral-300 hover:text-white"
                                    }`}
                                    onClick={() => setCycle(c)}
                                >
                                    {t(`membership.cycles.${c}`)}
                                    {cycleBadge.get(c) ? (
                                        <span className="ml-1 rounded bg-[#1a8a84] px-1 text-[9px] font-semibold text-white">
                                            {cycleBadge.get(c)}
                                        </span>
                                    ) : null}
                                </button>
                            ))}
                        </div>
                        <button type="button" className="hidden items-center gap-0.5 text-[12px] text-[#6eb8b2] sm:inline-flex" onClick={onClose}>
                            {t("membership.pointsMarket")}
                            <ChevronRight className="size-3.5" />
                        </button>
                    </div>
                </div>

                <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4 sm:px-6">
                    {audience === "team" ? (
                        <p className="py-16 text-center text-sm text-neutral-400">{t("membership.teamStub")}</p>
                    ) : loading ? (
                        <p className="py-16 text-center text-sm text-neutral-500">{t("common.loading")}</p>
                    ) : (
                        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-5">
                            {cards.map(({ plan, price, prices }) => {
                                const name = localeZh ? plan.name_zh : plan.name_en;
                                const multiVariant = prices.length > 1;
                                const highlight = plan.code === "ultra";
                                const priceYuan = (price!.price_fen / 100).toFixed(price!.price_fen % 100 === 0 ? 0 : 2);
                                const listYuan =
                                    price!.list_price_fen != null
                                        ? (price!.list_price_fen / 100).toFixed(price!.list_price_fen % 100 === 0 ? 0 : 2)
                                        : null;
                                const perMonth =
                                    cycle === "month"
                                        ? priceYuan
                                        : (price!.price_fen / (cycle === "year" ? 12 : 3) / 100).toFixed(0);
                                return (
                                    <div
                                        key={plan.code}
                                        className={`flex flex-col rounded-xl border p-4 ${
                                            highlight ? "border-[#E8867A]/60 bg-white/[0.04]" : "border-white/10 bg-white/[0.02]"
                                        }`}
                                    >
                                        <h3 className="text-[15px] font-semibold text-neutral-50">{name}</h3>
                                        <p className="mt-0.5 text-[11px] text-neutral-500">{t(`membership.tagline.${plan.code}`)}</p>
                                        <div className="mt-3 flex flex-wrap items-baseline gap-2">
                                            <span className="text-2xl font-semibold tabular-nums text-white">¥{priceYuan}</span>
                                            {listYuan ? (
                                                <span className="text-xs text-neutral-500 line-through">¥{listYuan}</span>
                                            ) : null}
                                        </div>
                                        <p className="mt-1 text-[11px] text-neutral-500">
                                            {t("membership.perMonth", { amount: perMonth })}
                                        </p>
                                        <div className="mt-4 rounded-lg border border-white/10 bg-black/30 px-3 py-3 text-center">
                                            <p className="text-lg font-bold tabular-nums text-white">
                                                {price!.credits_per_month.toLocaleString()}
                                            </p>
                                            <p className="text-[11px] text-neutral-500">{t("membership.creditsPerMonth")}</p>
                                        </div>
                                        {multiVariant ? (
                                            <div className="mt-2 flex gap-1 rounded-full bg-black/40 p-0.5">
                                                {prices.map((v) => (
                                                    <button
                                                        key={v.variant_code}
                                                        type="button"
                                                        className={`flex-1 rounded-full py-1 text-[10px] ${
                                                            v.variant_code === price!.variant_code
                                                                ? "bg-white/15 text-white"
                                                                : "text-neutral-500"
                                                        }`}
                                                        onClick={() =>
                                                            setVariants((prev) => ({ ...prev, [plan.code]: v.variant_code }))
                                                        }
                                                    >
                                                        {(v.credits_per_month / 1000).toFixed(1)}K
                                                    </button>
                                                ))}
                                            </div>
                                        ) : null}
                                        <button
                                            type="button"
                                            className={`mt-4 w-full rounded-full py-2 text-[13px] font-semibold transition hover:opacity-90 ${
                                                highlight ? "text-white" : "bg-white text-black"
                                            }`}
                                            style={highlight ? { background: CORAL } : undefined}
                                            onClick={() => void checkout(price!)}
                                        >
                                            {t("membership.cta")}
                                        </button>
                                        <p className="mt-3 text-[10px] leading-relaxed text-neutral-500">{t("membership.activityPlaceholder")}</p>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
                <p className="border-t border-white/[0.06] px-6 py-3 text-center text-[11px] text-neutral-500">{t("membership.footerReset")}</p>
            </div>
        </div>
    );
}
