import { App, Slider } from "antd";
import { Gift, SlidersHorizontal, Sparkles, Trophy, X, Zap } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { create } from "zustand";

import { meetsMinMembershipPlan } from "@/services/api/pilipala-membership";
import { createRechargeOrder } from "@/services/api/pilipala-recharge";
import { fetchCreditProducts, type CreditProduct } from "@/services/api/pilipala-credit-products";
import { fetchMembershipMe } from "@/services/api/pilipala-membership";
import { useAuthStore } from "@/stores/use-auth-store";

const CORAL = "#E8867A";
const TAB_META: Record<string, { icon: typeof Zap }> = {
    surprise: { icon: Gift },
    general: { icon: Zap },
    model: { icon: Trophy },
    custom: { icon: SlidersHorizontal },
};

type CreditSupermarketModalProps = {
    open: boolean;
    onClose: () => void;
};

export function CreditSupermarketModal({ open, onClose }: CreditSupermarketModalProps) {
    const { t, i18n } = useTranslation();
    const { message } = App.useApp();
    const user = useAuthStore((state) => state.user);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);
    const [loading, setLoading] = useState(false);
    const [products, setProducts] = useState<CreditProduct[]>([]);
    const [tab, setTab] = useState<string>("surprise");
    const [customCredits, setCustomCredits] = useState(600);
    const [userPlanCode, setUserPlanCode] = useState<string | null>(null);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setProducts(await fetchCreditProducts());
            if (user) {
                const me = await fetchMembershipMe().catch(() => null);
                setUserPlanCode(me?.plan_code ?? null);
            } else {
                setUserPlanCode(null);
            }
        } catch {
            message.error(t("creditStore.loadFailed"));
        } finally {
            setLoading(false);
        }
    }, [message, t, user]);

    useEffect(() => {
        if (open) void load();
    }, [open, load]);

    const tabs = useMemo(() => {
        const order = ["surprise", "general", "model", "custom"] as const;
        const present = new Set(products.map((p) => p.tab));
        return order.filter((k) => present.has(k));
    }, [products]);

    useEffect(() => {
        if (tabs.length && !tabs.includes(tab as typeof tabs[number])) {
            setTab(tabs[0]);
        }
    }, [tabs, tab]);

    const customProduct = useMemo(() => products.find((p) => p.tab === "custom"), [products]);
    const customMeta = useMemo(() => parseCustomMeta(customProduct?.meta_json), [customProduct]);
    const byTab = useMemo(() => {
        const map: Record<string, CreditProduct[]> = { general: [], surprise: [], model: [], custom: [] };
        for (const p of products) {
            if (map[p.tab]) map[p.tab].push(p);
            else map.general.push(p);
        }
        return map;
    }, [products]);

    const purchase = async (product: CreditProduct, credits?: number) => {
        if (!user) {
            setAuthModalOpen(true, "login");
            return;
        }
        try {
            const order = await createRechargeOrder({
                productCode: product.code,
                credits: credits ?? (product.tab === "custom" ? customCredits : undefined),
            });
            if (order.pay_url) {
                window.open(order.pay_url, "_blank", "noopener,noreferrer");
            } else {
                message.info(t("studio.recharge.unavailable"));
            }
        } catch (error) {
            message.error(error instanceof Error ? error.message : t("studio.recharge.failed"));
        }
    };

    const localeZh = i18n.language.startsWith("zh");

    if (!open) return null;

    return (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-3 sm:p-6">
            <button type="button" className="absolute inset-0 bg-black/70 backdrop-blur-sm" aria-label={t("common.close")} onClick={onClose} />
            <div className="relative z-[1] flex max-h-[92vh] w-full max-w-[960px] flex-col overflow-hidden rounded-2xl border border-white/10 bg-[#121212]/95 shadow-2xl">
                <div className="flex items-center justify-between border-b border-white/[0.06] px-5 py-4">
                    <div className="flex items-center gap-2">
                        <Sparkles className="size-4 text-[#6eb8b2]" />
                        <h2 className="text-base font-semibold text-neutral-100">{t("creditStore.title")}</h2>
                    </div>
                    <button
                        type="button"
                        className="inline-flex size-8 items-center justify-center rounded-full text-neutral-400 hover:bg-white/10"
                        onClick={onClose}
                    >
                        <X className="size-4" />
                    </button>
                </div>

                <div className="flex justify-center gap-1 border-b border-white/[0.06] px-4 py-3">
                    {tabs.map((key) => {
                        const Icon = TAB_META[key]?.icon ?? Zap;
                        const active = tab === key;
                        return (
                            <button
                                key={key}
                                type="button"
                                className={`inline-flex items-center gap-1.5 rounded-full px-4 py-1.5 text-[12px] transition ${
                                    active
                                        ? "studio-solid-on-white bg-white text-black"
                                        : "text-neutral-400 hover:bg-white/5 hover:text-neutral-200"
                                }`}
                                onClick={() => setTab(key)}
                            >
                                <Icon className="size-3.5" />
                                {t(`creditStore.tabs.${key}`)}
                            </button>
                        );
                    })}
                </div>

                <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4 sm:px-6">
                    {tab === "custom" ? (
                        <CustomSliderPanel
                            product={customProduct}
                            meta={customMeta}
                            credits={customCredits}
                            onCreditsChange={setCustomCredits}
                            onPurchase={() => customProduct && void purchase(customProduct, customCredits)}
                            localeZh={localeZh}
                            userPlanCode={userPlanCode}
                        />
                    ) : (
                        <ProductGrid
                            loading={loading}
                            products={byTab[tab] ?? []}
                            localeZh={localeZh}
                            userPlanCode={userPlanCode}
                            onPurchase={(p) => void purchase(p)}
                        />
                    )}
                </div>
            </div>
        </div>
    );
}

function ProductGrid({
    products,
    loading,
    localeZh,
    userPlanCode,
    onPurchase,
}: {
    products: CreditProduct[];
    loading: boolean;
    localeZh: boolean;
    userPlanCode: string | null;
    onPurchase: (p: CreditProduct) => void;
}) {
    const { t } = useTranslation();
    if (!loading && !products.length) {
        return <p className="py-12 text-center text-sm text-neutral-500">{t("creditStore.empty")}</p>;
    }
    return (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {products.map((product) => {
                const name = localeZh ? product.name_zh : product.name_en;
                const badge = localeZh ? product.badge_zh : product.badge_en;
                const priceYuan = (product.price_fen / 100).toFixed(product.price_fen % 100 === 0 ? 0 : 2);
                const listYuan =
                    product.list_price_fen != null
                        ? (product.list_price_fen / 100).toFixed(product.list_price_fen % 100 === 0 ? 0 : 2)
                        : null;
                const needsMembership = product.min_plan_code && !meetsMinMembershipPlan(userPlanCode, product.min_plan_code);
                return (
                    <div
                        key={product.code}
                        className="flex flex-col rounded-xl border border-white/10 bg-white/[0.03] p-4 transition hover:border-white/20"
                    >
                        <div className="flex items-start justify-between gap-2">
                            <div>
                                <h3 className="text-[15px] font-semibold text-neutral-50">{name}</h3>
                                <p className="mt-2 flex items-center gap-1 text-2xl font-bold tabular-nums text-white">
                                    <Zap className="size-4 text-[#6eb8b2]" />
                                    {product.credits.toLocaleString()}
                                </p>
                                <p className="mt-1 text-[11px] text-neutral-500">
                                    {product.validity_days ? t("creditStore.validDays", { days: product.validity_days }) : null}
                                </p>
                            </div>
                            {badge ? (
                                <span className="shrink-0 rounded-md px-2 py-0.5 text-[10px] font-medium text-white" style={{ background: CORAL }}>
                                    {badge}
                                </span>
                            ) : null}
                        </div>
                        {needsMembership ? (
                            <p className="mt-2 text-[11px] text-amber-400/90">{t("creditStore.membershipHint")}</p>
                        ) : null}
                        <div className="mt-4 flex items-end justify-between gap-3">
                            <div>
                                <span className="text-xl font-semibold tabular-nums text-white">¥{priceYuan}</span>
                                {listYuan ? <span className="ml-2 text-xs text-neutral-500 line-through">¥{listYuan}</span> : null}
                            </div>
                            <button
                                type="button"
                                className="rounded-full px-5 py-2 text-xs font-semibold text-white transition hover:opacity-90"
                                style={{ background: CORAL }}
                                onClick={() => onPurchase(product)}
                            >
                                {t("creditStore.buy")}
                            </button>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}

function CustomSliderPanel({
    product,
    meta,
    credits,
    onCreditsChange,
    onPurchase,
    localeZh,
    userPlanCode,
}: {
    product?: CreditProduct;
    meta: CustomMeta;
    credits: number;
    onCreditsChange: (v: number) => void;
    onPurchase: () => void;
    localeZh: boolean;
    userPlanCode: string | null;
}) {
    const { t } = useTranslation();
    if (!product) {
        return <p className="py-12 text-center text-sm text-neutral-500">{t("creditStore.empty")}</p>;
    }
    const name = localeZh ? product.name_zh : product.name_en;
    const priceFen = Math.floor((credits * 100) / meta.creditsPerYuan);
    const priceYuan = (priceFen / 100).toFixed(priceFen % 100 === 0 ? 0 : 2);
    const needsMembership = product.min_plan_code && !meetsMinMembershipPlan(userPlanCode, product.min_plan_code);
    return (
        <div className="rounded-xl border border-white/10 bg-white/[0.03] p-5 sm:p-6">
            <h3 className="text-sm font-semibold text-neutral-100">{name}</h3>
            <p className="mt-1 text-xs text-neutral-500">{t("creditStore.customRate", { rate: meta.creditsPerYuan })}</p>
            <div className="mt-8 px-1">
                <Slider
                    min={meta.minCredits}
                    max={meta.maxCredits}
                    step={100}
                    value={credits}
                    onChange={onCreditsChange}
                    styles={{ track: { background: CORAL }, rail: { background: "rgba(255,255,255,0.12)" } }}
                />
            </div>
            <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                <span className="text-lg font-bold tabular-nums text-white">{t("creditStore.credits", { count: credits })}</span>
                <button
                    type="button"
                    className="rounded-full px-6 py-2 text-sm font-semibold text-white"
                    style={{ background: CORAL }}
                    onClick={onPurchase}
                >
                    ¥{priceYuan} {t("creditStore.buy")}
                </button>
            </div>
            {needsMembership ? <p className="mt-3 text-[11px] text-amber-400/90">{t("creditStore.membershipHint")}</p> : null}
        </div>
    );
}

type CustomMeta = { minCredits: number; maxCredits: number; creditsPerYuan: number };

function parseCustomMeta(raw?: string | null): CustomMeta {
    const fallback = { minCredits: 600, maxCredits: 499995, creditsPerYuan: 15 };
    if (!raw) return fallback;
    try {
        const json = JSON.parse(raw) as Record<string, number>;
        return {
            minCredits: json.min_credits ?? fallback.minCredits,
            maxCredits: json.max_credits ?? fallback.maxCredits,
            creditsPerYuan: json.credits_per_yuan ?? fallback.creditsPerYuan,
        };
    } catch {
        return fallback;
    }
}

type CreditSupermarketUiState = {
    open: boolean;
    openStore: () => void;
    closeStore: () => void;
};

export const useCreditSupermarketUi = create<CreditSupermarketUiState>((set) => ({
    open: false,
    openStore: () => set({ open: true }),
    closeStore: () => set({ open: false }),
}));

export function useCreditSupermarket() {
    const open = useCreditSupermarketUi((s) => s.open);
    const openStore = useCreditSupermarketUi((s) => s.openStore);
    const closeStore = useCreditSupermarketUi((s) => s.closeStore);
    return { open, openStore, closeStore };
}
