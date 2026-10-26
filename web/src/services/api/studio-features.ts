import { pilipalaRequest } from "./pilipala-client";

export type StudioFeatures = {
    promo_enabled: boolean;
    promo_end_at: string;
    promo_title: string;
    promo_copy: string;
    promo_cta: string;
    membership_enabled: boolean;
    membership_badge: string;
};

export const STUDIO_FEATURE_DEFAULTS: StudioFeatures = {
    promo_enabled: true,
    promo_end_at: "2026-11-15T15:59:59.000Z",
    promo_title: "预付费加赠限时开启",
    promo_copy: "充值加赠最高 20%，任务成功才扣费",
    promo_cta: "限时抢购",
    membership_enabled: true,
    membership_badge: "限时 4.6 折",
};

function readVite(name: "VITE_STUDIO_PROMO_STRIP" | "VITE_STUDIO_PROMO_END_AT"): string {
    try {
        return import.meta.env[name]?.trim() ?? "";
    } catch {
        return "";
    }
}

/** 接口失败时的本地回退：默认开启，Vite 只作可选覆盖。 */
export function fallbackStudioFeatures(copy?: Partial<Pick<StudioFeatures, "promo_title" | "promo_copy" | "promo_cta" | "membership_badge">>): StudioFeatures {
    const strip = readVite("VITE_STUDIO_PROMO_STRIP");
    const endAt = readVite("VITE_STUDIO_PROMO_END_AT");
    return {
        ...STUDIO_FEATURE_DEFAULTS,
        promo_enabled: strip === "false" ? false : true,
        promo_end_at: endAt || STUDIO_FEATURE_DEFAULTS.promo_end_at,
        ...copy,
    };
}

export function parseStudioFeatures(data: unknown): StudioFeatures {
    const row = data && typeof data === "object" ? (data as Record<string, unknown>) : {};
    return {
        promo_enabled: readBoolean(row.promo_enabled, STUDIO_FEATURE_DEFAULTS.promo_enabled),
        promo_end_at: readString(row.promo_end_at, STUDIO_FEATURE_DEFAULTS.promo_end_at) || STUDIO_FEATURE_DEFAULTS.promo_end_at,
        promo_title: readString(row.promo_title, STUDIO_FEATURE_DEFAULTS.promo_title),
        promo_copy: readString(row.promo_copy, STUDIO_FEATURE_DEFAULTS.promo_copy),
        promo_cta: readString(row.promo_cta, STUDIO_FEATURE_DEFAULTS.promo_cta),
        membership_enabled: readBoolean(row.membership_enabled, STUDIO_FEATURE_DEFAULTS.membership_enabled),
        membership_badge: readString(row.membership_badge, STUDIO_FEATURE_DEFAULTS.membership_badge),
    };
}

export function parsePromoEndMs(iso: string | null | undefined): number {
    const ms = Date.parse(iso?.trim() || STUDIO_FEATURE_DEFAULTS.promo_end_at);
    return Number.isFinite(ms) ? ms : Date.parse(STUDIO_FEATURE_DEFAULTS.promo_end_at);
}

export function shouldShowStudioPromo(input: { promoEnabled: boolean; endAtMs: number; nowMs: number; dismissed: boolean }): boolean {
    return input.promoEnabled && !input.dismissed && input.nowMs < input.endAtMs;
}

export function shouldShowMembershipBadge(badge: string | null | undefined): boolean {
    return Boolean(badge?.trim());
}

let studioFeaturesCache: { data: StudioFeatures; at: number } | null = null;

export function fetchStudioFeatures(fetchImpl?: typeof fetch) {
    if (!fetchImpl && studioFeaturesCache && Date.now() - studioFeaturesCache.at < 60_000) {
        return Promise.resolve(studioFeaturesCache.data);
    }
    return pilipalaRequest<StudioFeatures>("/api/v1/studio/features", { fetchImpl }).then((data) => {
        const parsed = parseStudioFeatures(data);
        if (!fetchImpl) studioFeaturesCache = { data: parsed, at: Date.now() };
        return parsed;
    });
}

function readBoolean(value: unknown, fallback: boolean): boolean {
    if (typeof value === "boolean") return value;
    if (typeof value === "string") {
        if (value.toLowerCase() === "true") return true;
        if (value.toLowerCase() === "false") return false;
    }
    return fallback;
}

function readString(value: unknown, fallback: string): string {
    if (typeof value === "string") return value;
    if (value == null) return fallback;
    return String(value);
}
