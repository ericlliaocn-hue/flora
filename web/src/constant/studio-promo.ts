/** 预付费活动条倒计时截止时刻（ISO 8601）。可通过 `VITE_STUDIO_PROMO_END_AT` 覆盖。 */
const DEFAULT_PROMO_END_ISO = "2026-11-15T15:59:59.000Z";

function parsePromoEndMs(): number {
    const raw = import.meta.env.VITE_STUDIO_PROMO_END_AT?.trim();
    const iso = raw || DEFAULT_PROMO_END_ISO;
    const ms = Date.parse(iso);
    if (!Number.isFinite(ms)) {
        console.warn("[pilipala] Invalid VITE_STUDIO_PROMO_END_AT, using default promo deadline.");
        return Date.parse(DEFAULT_PROMO_END_ISO);
    }
    return ms;
}

export const STUDIO_PROMO_END_MS = parsePromoEndMs();
