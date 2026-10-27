import { App, Switch } from "antd";
import { Check, ChevronRight, X } from "lucide-react";
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

const CYCLES = ["year", "quarter", "month"] as const;
type Cycle = (typeof CYCLES)[number];

type MembershipModalProps = {
    open: boolean;
    onClose: () => void;
};

type ActivityRow = { ok: boolean; zh: string; en: string };

const PLAN_ACTIVITY: Record<string, ActivityRow[]> = {
    standard: [
        { ok: true, zh: "主力视频模型免费生成 3 条", en: "3 free flagship video gens" },
        { ok: true, zh: "标准生图模型不限次（低速）", en: "Standard image gen (slow queue)" },
        { ok: false, zh: "高阶视频模型会员价", en: "Premium video member pricing" },
    ],
    plus: [
        { ok: true, zh: "主力视频模型免费生成 8 条", en: "8 free flagship video gens" },
        { ok: true, zh: "主力视频模型限时 8 折", en: "20% off flagship video" },
        { ok: true, zh: "高阶生图模型限时 9 折", en: "10% off premium image" },
    ],
    pro: [
        { ok: true, zh: "主力视频模型免费生成 20 条", en: "20 free flagship video gens" },
        { ok: true, zh: "主力视频模型限时 5 折", en: "50% off flagship video" },
        { ok: true, zh: "高阶生图与视频模型限时 7 折", en: "30% off premium models" },
    ],
    deluxe: [
        { ok: true, zh: "主力视频模型免费生成 40 条", en: "40 free flagship video gens" },
        { ok: true, zh: "主力视频模型限时 3 折", en: "70% off flagship video" },
        { ok: true, zh: "高阶生图与视频模型限时 6 折", en: "40% off premium models" },
        { ok: true, zh: "专属加速队列", en: "Priority queue" },
    ],
    ultra: [
        { ok: true, zh: "主力视频模型免费生成 80 条", en: "80 free flagship video gens" },
        { ok: true, zh: "主力视频模型限时 2 折", en: "80% off flagship video" },
        { ok: true, zh: "全站高阶模型触底折扣", en: "Lowest premium model rates" },
        { ok: true, zh: "加赠积分按月叠加发放", en: "Bonus credits each month" },
    ],
};

export function useMembershipModal() {
    const [open, setOpen] = useState(false);
    return { open, openMembership: () => setOpen(true), closeMembership: () => setOpen(false) };
}

function formatYuan(fen: number): string {
    const yuan = fen / 100;
    return yuan % 1 === 0 ? String(Math.round(yuan)) : yuan.toFixed(2);
}

function cycleMonths(cycle: Cycle): number {
    if (cycle === "year") return 12;
    if (cycle === "quarter") return 3;
    return 1;
}

function cycleLabel(cycle: Cycle, localeZh: boolean): string {
    if (localeZh) return ({ year: "年", quarter: "季", month: "月" } as const)[cycle];
    return ({ year: "yr", quarter: "qtr", month: "mo" } as const)[cycle];
}

function formatCreditsK(credits: number, localeZh: boolean): string {
    const k = credits / 1000;
    const kStr = Number.isInteger(k) ? String(k) : k.toFixed(1);
    return `${kStr}K${localeZh ? "/月" : "/mo"}`;
}

function defaultVariantCode(prices: MembershipPlanPrice[], cycle: Cycle): string {
    if (!prices.length) return "default";
    return cycle === "month" ? prices[0].variant_code : prices[prices.length - 1].variant_code;
}

function perCreditYuan(price: MembershipPlanPrice, cycle: Cycle): string {
    const gift = price.gift_months ?? 0;
    const totalMonths = cycleMonths(cycle) + gift;
    const perCredit = (price.price_fen / 100) / (price.credits_per_month * totalMonths);
    const rounded = Number(perCredit.toFixed(3));
    return String(rounded);
}

function parseBenefits(plan: MembershipPlan, localeZh: boolean): string[] {
    if (!plan.benefits_json) return [];
    try {
        const rows = JSON.parse(plan.benefits_json) as { zh?: string; en?: string }[];
        if (!Array.isArray(rows)) return [];
        return rows.map((r) => (localeZh ? r.zh : r.en) ?? "").filter(Boolean);
    } catch {
        return [];
    }
}

export function MembershipModal({ open, onClose }: MembershipModalProps) {
    const { t, i18n } = useTranslation();
    const { message } = App.useApp();
    const user = useAuthStore((state) => state.user);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);
    const [loading, setLoading] = useState(false);
    const [plans, setPlans] = useState<MembershipPlan[]>([]);
    const [cycle, setCycle] = useState<Cycle>("year");
    const [teamMode, setTeamMode] = useState(false);
    const [variants, setVariants] = useState<Record<string, string>>({});

    const localeZh = i18n.language.startsWith("zh");
    const audience = teamMode ? "team" : "personal";

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

    useEffect(() => {
        if (!open) return;
        setVariants((prev) => {
            const next = { ...prev };
            for (const plan of plans.filter((p) => p.audience === "personal")) {
                const prices = plan.prices.filter((p) => p.cycle === cycle);
                if (prices.length <= 1) continue;
                const current = prev[plan.code];
                if (!current || !prices.some((p) => p.variant_code === current)) {
                    next[plan.code] = defaultVariantCode(prices, cycle);
                }
            }
            return next;
        });
    }, [cycle, open, plans]);

    const cards = useMemo(() => {
        return plans
            .filter((p) => p.audience === audience)
            .map((plan) => {
                const prices = plan.prices.filter((p) => p.cycle === cycle);
                const variantCode = variants[plan.code] ?? defaultVariantCode(prices, cycle);
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
                className="relative z-[1] flex max-h-[92vh] w-full max-w-[1180px] flex-col overflow-hidden rounded-2xl border border-white/10 bg-[#0d0d0d]/98 shadow-2xl"
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

                <div className="border-b border-white/[0.06] px-5 pb-4 pt-5 sm:px-8">
                    <p className="text-center text-[13px] text-neutral-400">{t("membership.promoLine")}</p>
                    <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                        <label className="flex items-center gap-2 text-[12px] text-neutral-400">
                            <span>{t("membership.team")}</span>
                            <Switch size="small" checked={teamMode} onChange={setTeamMode} />
                        </label>

                        <div className="mx-auto flex rounded-full bg-white/[0.06] p-1">
                            {CYCLES.map((c) => {
                                const active = cycle === c;
                                const discountKey = `membership.cycleDiscount.${c}` as const;
                                const discount = t(discountKey);
                                const yearHighlight = c === "year";
                                return (
                                    <button
                                        key={c}
                                        type="button"
                                        className={`rounded-full px-4 py-1.5 text-[12px] transition ${
                                            active
                                                ? "bg-white/[0.12] font-semibold text-white"
                                                : "text-neutral-400 hover:text-neutral-200"
                                        }`}
                                        onClick={() => setCycle(c)}
                                    >
                                        {t(`membership.cycles.${c}`)}
                                        {discount ? (
                                            <span
                                                className={`ml-1 text-[11px] font-semibold ${
                                                    yearHighlight ? "text-[#ff8a65]" : "text-neutral-500"
                                                }`}
                                            >
                                                {discount}
                                            </span>
                                        ) : null}
                                    </button>
                                );
                            })}
                        </div>

                        <div className="hidden items-center gap-3 text-[12px] sm:flex">
                            <button type="button" className="inline-flex items-center gap-0.5 text-neutral-400 hover:text-neutral-200">
                                {t("membership.enterprise")}
                                <ChevronRight className="size-3.5" />
                            </button>
                            <button
                                type="button"
                                className="inline-flex items-center gap-0.5 text-[#6eb8b2] hover:text-[#8ed4ce]"
                                onClick={onClose}
                            >
                                {t("membership.pointsMarket")}
                                <ChevronRight className="size-3.5" />
                            </button>
                        </div>
                    </div>
                </div>

                <div className="min-h-0 flex-1 overflow-y-auto px-3 py-4 sm:px-5">
                    {audience === "team" ? (
                        <p className="py-16 text-center text-sm text-neutral-400">{t("membership.teamStub")}</p>
                    ) : loading ? (
                        <p className="py-16 text-center text-sm text-neutral-500">{t("common.loading")}</p>
                    ) : (
                        <div className="grid grid-cols-1 gap-2 md:grid-cols-2 xl:grid-cols-5">
                            {cards.map(({ plan, price, prices }) => (
                                <PlanCard
                                    key={plan.code}
                                    plan={plan}
                                    price={price!}
                                    prices={prices}
                                    cycle={cycle}
                                    localeZh={localeZh}
                                    onSelectVariant={(code) => setVariants((prev) => ({ ...prev, [plan.code]: code }))}
                                    onCheckout={() => void checkout(price!)}
                                />
                            ))}
                        </div>
                    )}
                </div>
                <p className="border-t border-white/[0.06] px-6 py-3 text-center text-[11px] text-neutral-500">{t("membership.footerReset")}</p>
            </div>
        </div>
    );
}

function PlanCard({
    plan,
    price,
    prices,
    cycle,
    localeZh,
    onSelectVariant,
    onCheckout,
}: {
    plan: MembershipPlan;
    price: MembershipPlanPrice;
    prices: MembershipPlanPrice[];
    cycle: Cycle;
    localeZh: boolean;
    onSelectVariant: (code: string) => void;
    onCheckout: () => void;
}) {
    const { t } = useTranslation();
    const name = localeZh ? plan.name_zh : plan.name_en;
    const multiVariant = prices.length > 1;
    const isDeluxe = plan.code === "deluxe";
    const isUltra = plan.code === "ultra";

    const priceYuan = formatYuan(price.price_fen);
    const listYuan = price.list_price_fen != null ? formatYuan(price.list_price_fen) : null;
    const renewYuan = price.renew_price_fen != null ? formatYuan(price.renew_price_fen) : null;
    const giftMonths = price.gift_months ?? 0;

    const billingMonths = cycleMonths(cycle) + giftMonths;
    const effectivePerMonth = Math.round(price.price_fen / 100 / billingMonths);

    const subtitle = useMemo(() => {
        if (giftMonths > 0 && cycle === "year" && isUltra) {
            return localeZh
                ? `¥${effectivePerMonth}/月  一次性付费`
                : `¥${effectivePerMonth}/mo  one-time payment`;
        }
        if (cycle === "year" && renewYuan) {
            return localeZh
                ? `¥${effectivePerMonth}/月  次年续费 ¥${renewYuan}  可随时取消`
                : `¥${effectivePerMonth}/mo  renews at ¥${renewYuan}`;
        }
        if (cycle === "quarter" && renewYuan) {
            return localeZh
                ? `¥${effectivePerMonth}/月  下季续费 ¥${renewYuan}  可随时取消`
                : `¥${effectivePerMonth}/mo  renews ¥${renewYuan}`;
        }
        return localeZh ? `¥${effectivePerMonth}/月` : `¥${effectivePerMonth}/mo`;
    }, [cycle, renewYuan, effectivePerMonth, giftMonths, isUltra, localeZh]);

    const activityRows = PLAN_ACTIVITY[plan.code] ?? [];
    const benefitLines = parseBenefits(plan, localeZh);
    const approxVideos = Math.round(price.credits_per_month / 10);
    const creditRate = perCreditYuan(price, cycle);

    const borderClass = isUltra
        ? "border-[#E8867A]/70"
        : isDeluxe
          ? "border-[#38bdf8]/60"
          : "border-white/10";

    const badgeClass = isUltra
        ? "bg-[#E8867A]/20 text-[#E8867A]"
        : isDeluxe
          ? "bg-[#1a8a84]/25 text-[#5ce1e6]"
          : "";

    const activityBoxClass = isUltra
        ? "border-[#E8867A]/25 bg-[#E8867A]/[0.04]"
        : isDeluxe
          ? "border-[#38bdf8]/20 bg-[#38bdf8]/[0.04]"
          : "border-white/[0.08] bg-white/[0.02]";

    const activityTitleClass = isUltra ? "text-[#E8867A]" : isDeluxe ? "text-[#5ce1e6]" : "text-[#6eb8b2]";

    return (
        <div className={`flex min-w-0 flex-col rounded-xl border bg-white/[0.02] p-4 ${borderClass}`}>
            {isDeluxe ? (
                <div className="-mx-4 -mt-4 mb-3 h-2 rounded-t-xl bg-gradient-to-r from-[#2bb8d6] to-[#5ce1e6]" />
            ) : isUltra ? (
                <div className="-mx-4 -mt-4 mb-3 h-2 rounded-t-xl bg-gradient-to-r from-[#ff7a2f] to-[#ff4d00]" />
            ) : (
                <div className="mb-3 mt-5 h-2" aria-hidden />
            )}

            <div className="flex items-start justify-between gap-2">
                <div>
                    <h3 className="text-[16px] font-semibold leading-tight text-white">{name}</h3>
                    <p className="mt-0.5 text-[11px] text-neutral-500">{t(`membership.tagline.${plan.code}`)}</p>
                </div>
                {price.badge_zh ? (
                    <span className={`shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium ${badgeClass}`}>{price.badge_zh}</span>
                ) : null}
            </div>

            <div className="mt-3 flex flex-wrap items-baseline gap-1.5">
                <span className="text-[28px] font-bold leading-none tabular-nums text-white">¥{priceYuan}</span>
                <span className="text-[13px] text-neutral-300">/{cycleLabel(cycle, localeZh)}</span>
                {listYuan ? <span className="text-[12px] text-neutral-500 line-through">¥{listYuan}</span> : null}
            </div>
            <p className="mt-2 text-[11px] leading-snug text-neutral-500">{subtitle}</p>

            <div className="mt-4 flex h-7 items-center">
                {multiVariant ? (
                    <div className="flex w-full gap-0.5 rounded-full bg-white/[0.06] p-0.5">
                        {prices.map((v) => {
                            const selected = v.variant_code === price.variant_code;
                            const gift = v.gift_months ?? 0;
                            return (
                                <div key={v.variant_code} className="relative flex-1">
                                    {gift > 0 ? (
                                        <span
                                            className={`pointer-events-none absolute -top-3.5 left-1/2 z-[1] -translate-x-1/2 whitespace-nowrap rounded px-1 py-px text-[9px] font-medium ${
                                                selected ? "bg-[#E8867A] text-white" : "bg-white/15 text-neutral-200"
                                            }`}
                                        >
                                            {t("membership.giftMonths", { count: gift })}
                                        </span>
                                    ) : null}
                                    <button
                                        type="button"
                                        className={`w-full rounded-full py-1 text-[11px] transition ${
                                            selected
                                                ? "studio-solid-on-white bg-white font-semibold text-black shadow-sm"
                                                : "text-neutral-400 hover:text-neutral-200"
                                        }`}
                                        onClick={() => onSelectVariant(v.variant_code)}
                                    >
                                        {formatCreditsK(v.credits_per_month, localeZh)}
                                    </button>
                                </div>
                            );
                        })}
                    </div>
                ) : null}
            </div>

            <div className="mt-4 rounded-xl border border-white/[0.06] bg-white/[0.04] px-3.5 py-3">
                <p className="flex flex-wrap items-baseline gap-1.5">
                    <span className="text-[22px] font-bold tabular-nums text-white">
                        {price.credits_per_month.toLocaleString()}
                    </span>
                    <span className="text-[12px] text-neutral-400">{t("membership.creditsPerMonth")}</span>
                </p>
                <p className="mt-1 text-[11px] tabular-nums text-neutral-500">
                    {localeZh
                        ? `1积分 ≈ ¥${creditRate}  |  ≈ ${approxVideos} 个视频`
                        : `1 credit ≈ ¥${creditRate}  |  ≈ ${approxVideos} videos`}
                </p>
            </div>

            <button
                type="button"
                className={`mt-4 w-full rounded-full py-2.5 text-[13px] font-semibold transition ${
                    isUltra
                        ? "bg-[#E8867A] text-white hover:opacity-95"
                        : "studio-solid-on-white bg-white text-black hover:bg-neutral-200"
                }`}
                onClick={onCheckout}
            >
                {t("membership.cta")}
            </button>

            <div className="mt-4 flex flex-1 flex-col">
                <p className={`text-[11px] font-medium ${activityTitleClass}`}>🎁 {t("membership.limitedActivity")}</p>
                <div className={`mt-2 rounded-xl border p-3 ${activityBoxClass}`}>
                    {isUltra ? (
                        <span className="mb-2 inline-block rounded bg-[#E8867A]/25 px-1.5 py-0.5 text-[9px] font-medium text-[#E8867A]">
                            {t("membership.lowestPriceTag")}
                        </span>
                    ) : null}
                    <ul className="space-y-1.5 text-[10px] leading-relaxed text-neutral-300">
                        {activityRows.map((row, i) => (
                            <li key={i} className="flex gap-1.5">
                                <span className={row.ok ? "text-emerald-400" : "text-neutral-600"}>{row.ok ? "✓" : "×"}</span>
                                <span className={row.ok ? "" : "text-neutral-500"}>{localeZh ? row.zh : row.en}</span>
                            </li>
                        ))}
                    </ul>
                </div>
                <ul className="mt-3 space-y-1.5 text-[10px] text-neutral-400">
                    {benefitLines.map((line, i) => (
                        <li key={i} className="flex items-start gap-1.5">
                            <Check className="mt-0.5 size-3 shrink-0 text-neutral-300" strokeWidth={2.5} />
                            <span>{line}</span>
                        </li>
                    ))}
                    {plan.storage_gb > 0 ? (
                        <li className="flex items-start gap-1.5">
                            <Check className="mt-0.5 size-3 shrink-0 text-neutral-300" strokeWidth={2.5} />
                            <span>{t("membership.storageGb", { gb: plan.storage_gb })}</span>
                        </li>
                    ) : null}
                </ul>
            </div>
        </div>
    );
}
