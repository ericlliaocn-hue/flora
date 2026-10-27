import { expect, test } from "bun:test";

const {
    STUDIO_FEATURE_DEFAULTS,
    fallbackStudioFeatures,
    fetchStudioFeatures,
    parsePromoEndMs,
    parseStudioFeatures,
    shouldShowMembershipBadge,
    shouldShowStudioPromo,
} = await import("../src/services/api/studio-features");

test("parseStudioFeatures fills brief defaults and keeps snake_case fields", () => {
    const parsed = parseStudioFeatures({
        promo_enabled: true,
        promo_end_at: "2026-11-15T15:59:59.000Z",
        promo_title: "预付费加赠限时开启",
        promo_copy: "充值加赠最高 20%，任务成功才扣费",
        promo_cta: "限时抢购",
        membership_enabled: true,
        membership_badge: "限时 4.6 折",
        enterprise_enabled: false,
    });
    expect(parsed).toEqual(STUDIO_FEATURE_DEFAULTS);
});

test("parseStudioFeatures defaults enterprise procurement to off", () => {
    expect(parseStudioFeatures({}).enterprise_enabled).toBe(false);
    expect(parseStudioFeatures({ enterprise_enabled: true }).enterprise_enabled).toBe(true);
});

test("parseStudioFeatures uses defaults for missing keys and keeps an empty membership badge", () => {
    expect(parseStudioFeatures({})).toEqual(STUDIO_FEATURE_DEFAULTS);
    expect(parseStudioFeatures({ membership_badge: "" }).membership_badge).toBe("");
    expect(parseStudioFeatures({ promo_enabled: false }).promo_enabled).toBe(false);
});

test("empty membership badge hides the corner mark", () => {
    expect(shouldShowMembershipBadge("限时 4.6 折")).toBe(true);
    expect(shouldShowMembershipBadge("")).toBe(false);
    expect(shouldShowMembershipBadge("   ")).toBe(false);
    expect(shouldShowMembershipBadge(null)).toBe(false);
});

test("promo strip hides when disabled, expired, or dismissed", () => {
    const endAtMs = Date.parse("2026-11-15T15:59:59.000Z");
    expect(shouldShowStudioPromo({ promoEnabled: true, endAtMs, nowMs: endAtMs - 1000, dismissed: false })).toBe(true);
    expect(shouldShowStudioPromo({ promoEnabled: false, endAtMs, nowMs: endAtMs - 1000, dismissed: false })).toBe(false);
    expect(shouldShowStudioPromo({ promoEnabled: true, endAtMs, nowMs: endAtMs + 1, dismissed: false })).toBe(false);
    expect(shouldShowStudioPromo({ promoEnabled: true, endAtMs, nowMs: endAtMs - 1000, dismissed: true })).toBe(false);
});

test("fallbackStudioFeatures stays on by default", () => {
    expect(fallbackStudioFeatures().promo_enabled).toBe(true);
    expect(parsePromoEndMs("not-a-date")).toBe(Date.parse(STUDIO_FEATURE_DEFAULTS.promo_end_at));
});

test("fetchStudioFeatures reads GET /api/v1/studio/features", async () => {
    const calls: string[] = [];
    const impl = (async (input: RequestInfo | URL) => {
        calls.push(typeof input === "string" ? input : input.toString());
        return new Response(
            JSON.stringify({
                ok: true,
                data: {
                    promo_enabled: false,
                    promo_end_at: "2026-12-01T00:00:00.000Z",
                    promo_title: "自定义标题",
                    promo_copy: "自定义副文案",
                    promo_cta: "马上充值",
                    membership_enabled: true,
                    membership_badge: "",
                    enterprise_enabled: false,
                },
                error_code: null,
                message: null,
            }),
            { status: 200, headers: { "Content-Type": "application/json" } },
        );
    }) as typeof fetch;

    const features = await fetchStudioFeatures(impl);
    expect(calls[0]).toBe("/api/v1/studio/features");
    expect(features.promo_enabled).toBe(false);
    expect(features.membership_badge).toBe("");
    expect(features.promo_title).toBe("自定义标题");
});
