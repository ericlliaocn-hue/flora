import { Check } from "lucide-react";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";

import type { MembershipPlan, MembershipPlanPrice } from "@/services/api/pilipala-membership";

export const MEMBERSHIP_CYCLES = ["year", "quarter", "month"] as const;
export type MembershipCycle = (typeof MEMBERSHIP_CYCLES)[number];

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

export function formatYuan(fen: number): string {
    const yuan = fen / 100;
    return yuan % 1 === 0 ? String(Math.round(yuan)) : yuan.toFixed(2);
}

export function cycleMonths(cycle: MembershipCycle): number {
    if (cycle === "year") return 12;
    if (cycle === "quarter") return 3;
    return 1;
}

function cycleLabel(cycle: MembershipCycle, localeZh: boolean): string {
    if (localeZh) return ({ year: "年", quarter: "季", month: "月" } as const)[cycle];
    return ({ year: "yr", quarter: "qtr", month: "mo" } as const)[cycle];
}

function formatCreditsK(credits: number, localeZh: boolean): string {
    const k = credits / 1000;
    const kStr = Number.isInteger(k) ? String(k) : k.toFixed(1);
    return `${kStr}K${localeZh ? "/月" : "/mo"}`;
}

export function defaultVariantCode(prices: MembershipPlanPrice[], cycle: MembershipCycle): string {
    if (!prices.length) return "default";
    return cycle === "month" ? prices[0].variant_code : prices[prices.length - 1].variant_code;
}

function perCreditYuan(price: MembershipPlanPrice, cycle: MembershipCycle): string {
    const gift = price.gift_months ?? 0;
    const totalMonths = cycleMonths(cycle) + gift;
    const perCredit = price.price_fen / 100 / (price.credits_per_month * totalMonths);
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

export function MembershipPlanCard({
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
    cycle: MembershipCycle;
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
        <div className={`flex h-full min-h-0 min-w-0 flex-col rounded-2xl border bg-white/[0.02] p-5 ${borderClass}`}>
            <div className="-mx-5 -mt-5 mb-4 h-2 shrink-0 overflow-hidden rounded-t-2xl" aria-hidden>
                {isDeluxe ? <div className="h-full bg-gradient-to-r from-[#2bb8d6] to-[#5ce1e6]" /> : null}
                {isUltra ? <div className="h-full bg-gradient-to-r from-[#ff7a2f] to-[#ff4d00]" /> : null}
            </div>

            <div className="flex min-h-[46px] shrink-0 items-start justify-between gap-2">
                <div className="min-w-0">
                    <h3 className="text-[17px] font-semibold leading-tight text-white">{name}</h3>
                    <p className="mt-0.5 line-clamp-2 text-[12px] text-neutral-500">{t(`membership.tagline.${plan.code}`)}</p>
                </div>
                {price.badge_zh ? (
                    <span className={`shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium ${badgeClass}`}>{price.badge_zh}</span>
                ) : null}
            </div>

            <div className="mt-4 flex h-9 shrink-0 flex-wrap items-baseline gap-1.5">
                <span className="text-[30px] font-bold leading-none tabular-nums text-white">¥{priceYuan}</span>
                <span className="text-[14px] text-neutral-300">/{cycleLabel(cycle, localeZh)}</span>
                {listYuan ? <span className="text-[12px] text-neutral-500 line-through">¥{listYuan}</span> : null}
            </div>
            <p className="mt-2 min-h-[2.75rem] shrink-0 text-[11px] leading-snug text-neutral-500 line-clamp-3">{subtitle}</p>

            <div className="relative mt-4 h-11 shrink-0">
                {multiVariant ? (
                    <div className="absolute inset-x-0 bottom-0 flex gap-0.5 rounded-full bg-white/[0.06] p-0.5">
                        {prices.map((v) => {
                            const selected = v.variant_code === price.variant_code;
                            const gift = v.gift_months ?? 0;
                            return (
                                <div key={v.variant_code} className="relative flex-1">
                                    {gift > 0 ? (
                                        <span
                                            className={`pointer-events-none absolute bottom-full left-1/2 z-[1] mb-0.5 -translate-x-1/2 whitespace-nowrap rounded px-1 py-px text-[9px] font-medium ${
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

            <div className="mt-4 shrink-0 rounded-xl border border-white/[0.06] bg-white/[0.04] px-3.5 py-3 min-h-[4.75rem]">
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
                className={`mt-4 shrink-0 w-full rounded-full py-2.5 text-[13px] font-semibold transition ${
                    isUltra
                        ? "bg-[#E8867A] text-white hover:opacity-95"
                        : "studio-solid-on-white bg-white text-black hover:bg-neutral-200"
                }`}
                onClick={onCheckout}
            >
                {t("membership.cta")}
            </button>

            <div className="mt-4 flex min-h-0 flex-1 flex-col">
                <p className={`shrink-0 text-[11px] font-medium ${activityTitleClass}`}>🎁 {t("membership.limitedActivity")}</p>
                <div
                    className={`mt-2 flex min-h-[7.5rem] flex-1 flex-col overflow-hidden rounded-xl border p-3 ${activityBoxClass}`}
                >
                    <div className="mb-2 h-[1.125rem] shrink-0">
                        {isUltra ? (
                            <span className="inline-block rounded bg-[#E8867A]/25 px-1.5 py-0.5 text-[9px] font-medium text-[#E8867A]">
                                {t("membership.lowestPriceTag")}
                            </span>
                        ) : null}
                    </div>
                    <ul className="min-h-0 flex-1 space-y-1.5 overflow-y-auto text-[10px] leading-relaxed text-neutral-300">
                        {activityRows.map((row, i) => (
                            <li key={i} className="flex gap-1.5">
                                <span className={row.ok ? "text-emerald-400" : "text-neutral-600"}>{row.ok ? "✓" : "×"}</span>
                                <span className={row.ok ? "" : "text-neutral-500"}>{localeZh ? row.zh : row.en}</span>
                            </li>
                        ))}
                    </ul>
                </div>
                <ul className="mt-3 max-h-[5.5rem] shrink-0 space-y-1.5 overflow-y-auto text-[10px] text-neutral-400">
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
