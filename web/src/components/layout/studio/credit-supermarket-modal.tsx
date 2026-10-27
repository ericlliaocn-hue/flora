import { App, Modal, Slider, Tabs } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { createRechargeOrder } from "@/services/api/pilipala-recharge";
import { fetchCreditProducts, type CreditProduct } from "@/services/api/pilipala-credit-products";
import { useAuthStore } from "@/stores/use-auth-store";

const TABS = ["general", "surprise", "custom"] as const;
const CORAL = "#E8867A";

type CreditSupermarketModalProps = {
    open: boolean;
    onClose: () => void;
};

export function CreditSupermarketModal({ open, onClose }: CreditSupermarketModalProps) {
    const { t, i18n } = useTranslation();
    const { message } = App.useApp();
    const [loading, setLoading] = useState(false);
    const [products, setProducts] = useState<CreditProduct[]>([]);
    const [tab, setTab] = useState<string>("general");
    const [customCredits, setCustomCredits] = useState(600);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setProducts(await fetchCreditProducts());
        } catch {
            message.error(t("creditStore.loadFailed"));
        } finally {
            setLoading(false);
        }
    }, [message, t]);

    useEffect(() => {
        if (open) void load();
    }, [open, load]);

    const customProduct = useMemo(() => products.find((p) => p.tab === "custom"), [products]);
    const customMeta = useMemo(() => parseCustomMeta(customProduct?.meta_json), [customProduct]);
    const byTab = useMemo(() => {
        const map: Record<string, CreditProduct[]> = { general: [], surprise: [], custom: [] };
        for (const p of products) {
            if (p.tab === "custom") map.custom.push(p);
            else if (p.tab === "surprise") map.surprise.push(p);
            else map.general.push(p);
        }
        return map;
    }, [products]);

    const purchase = async (product: CreditProduct, credits?: number) => {
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

    return (
        <Modal
            open={open}
            onCancel={onClose}
            footer={null}
            width={720}
            title={t("creditStore.title")}
            destroyOnHidden
            className="credit-supermarket-modal"
        >
            <Tabs
                activeKey={tab}
                onChange={setTab}
                items={TABS.map((key) => ({
                    key,
                    label: t(`creditStore.tabs.${key}`),
                    children:
                        key === "custom" ? (
                            <CustomSliderPanel
                                product={customProduct}
                                meta={customMeta}
                                credits={customCredits}
                                onCreditsChange={setCustomCredits}
                                onPurchase={() => customProduct && void purchase(customProduct, customCredits)}
                                localeZh={localeZh}
                            />
                        ) : (
                            <ProductGrid
                                loading={loading}
                                products={byTab[key]}
                                localeZh={localeZh}
                                onPurchase={(p) => void purchase(p)}
                            />
                        ),
                }))}
            />
        </Modal>
    );
}

function ProductGrid({
    products,
    loading,
    localeZh,
    onPurchase,
}: {
    products: CreditProduct[];
    loading: boolean;
    localeZh: boolean;
    onPurchase: (p: CreditProduct) => void;
}) {
    const { t } = useTranslation();
    if (!loading && !products.length) {
        return <p className="py-8 text-center text-sm text-stone-500">{t("creditStore.empty")}</p>;
    }
    return (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {products.map((product) => {
                const name = localeZh ? product.name_zh : product.name_en;
                const badge = localeZh ? product.badge_zh : product.badge_en;
                const priceYuan = (product.price_fen / 100).toFixed(product.price_fen % 100 === 0 ? 0 : 2);
                return (
                    <div
                        key={product.code}
                        className="flex flex-col rounded-xl border border-stone-200 p-4 dark:border-stone-700"
                    >
                        <div className="flex items-start justify-between gap-2">
                            <div>
                                <h3 className="text-sm font-semibold text-stone-900 dark:text-stone-100">{name}</h3>
                                <p className="mt-1 text-xs text-stone-500">
                                    {t("creditStore.credits", { count: product.credits })}
                                    {product.validity_days
                                        ? ` · ${t("creditStore.validDays", { days: product.validity_days })}`
                                        : null}
                                </p>
                            </div>
                            {badge ? (
                                <span className="shrink-0 rounded px-1.5 py-0.5 text-[10px] font-medium text-white" style={{ background: CORAL }}>
                                    {badge}
                                </span>
                            ) : null}
                        </div>
                        {product.min_plan_code ? (
                            <p className="mt-2 text-[11px] text-amber-700 dark:text-amber-400">{t("creditStore.membershipHint")}</p>
                        ) : null}
                        <div className="mt-3 flex items-center justify-between">
                            <span className="text-base font-semibold tabular-nums text-stone-900 dark:text-stone-50">¥{priceYuan}</span>
                            <button
                                type="button"
                                className="rounded-full px-4 py-1.5 text-xs font-semibold text-white transition hover:opacity-90"
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
}: {
    product?: CreditProduct;
    meta: CustomMeta;
    credits: number;
    onCreditsChange: (v: number) => void;
    onPurchase: () => void;
    localeZh: boolean;
}) {
    const { t } = useTranslation();
    if (!product) {
        return <p className="py-8 text-center text-sm text-stone-500">{t("creditStore.empty")}</p>;
    }
    const name = localeZh ? product.name_zh : product.name_en;
    const priceFen = Math.floor((credits * 100) / meta.creditsPerYuan);
    const priceYuan = (priceFen / 100).toFixed(priceFen % 100 === 0 ? 0 : 2);
    return (
        <div className="rounded-xl border border-stone-200 p-5 dark:border-stone-700">
            <h3 className="text-sm font-semibold">{name}</h3>
            <p className="mt-1 text-xs text-stone-500">
                {t("creditStore.customRate", { rate: meta.creditsPerYuan })}
            </p>
            <div className="mt-6 px-1">
                <Slider
                    min={meta.minCredits}
                    max={meta.maxCredits}
                    step={100}
                    value={credits}
                    onChange={onCreditsChange}
                    styles={{ track: { background: CORAL } }}
                />
            </div>
            <div className="mt-2 flex items-center justify-between text-sm">
                <span className="tabular-nums text-stone-700 dark:text-stone-200">
                    {t("creditStore.credits", { count: credits })}
                </span>
                <span className="font-semibold tabular-nums">¥{priceYuan}</span>
            </div>
            <button
                type="button"
                className="mt-4 w-full rounded-full py-2 text-sm font-semibold text-white"
                style={{ background: CORAL }}
                onClick={onPurchase}
            >
                {t("creditStore.buy")}
            </button>
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

export function useCreditSupermarket() {
    const user = useAuthStore((state) => state.user);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);
    const [open, setOpen] = useState(false);

    const openStore = () => {
        if (!user) {
            setAuthModalOpen(true, "login");
            return;
        }
        setOpen(true);
    };

    return { open, openStore, closeStore: () => setOpen(false) };
}
