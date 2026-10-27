import { pilipalaRequest } from "./pilipala-client";
import type { RechargeOrder } from "./pilipala-recharge";

export type MembershipPlanPrice = {
    id: number;
    cycle: "month" | "quarter" | "year";
    variant_code: string;
    credits_per_month: number;
    price_fen: number;
    list_price_fen?: number | null;
    renew_price_fen?: number | null;
    badge_zh?: string | null;
    sort_order: number;
};

export type MembershipPlan = {
    id: number;
    code: string;
    name_zh: string;
    name_en: string;
    sort_order: number;
    credits_per_month: number;
    concurrency?: number | null;
    storage_gb: number;
    audience: string;
    benefits_json?: string | null;
    prices: MembershipPlanPrice[];
};

export type MembershipMe = {
    plan_code: string;
    cycle: string;
    variant_code: string;
    status: string;
    credits_per_month: number;
    current_period_start: string;
    current_period_end: string;
    last_grant_at?: string | null;
};

const PLAN_RANK: Record<string, number> = {
    standard: 1,
    plus: 2,
    pro: 3,
    deluxe: 4,
    ultra: 5,
};

export function membershipPlanRank(code: string | null | undefined): number {
    if (!code) return 0;
    return PLAN_RANK[code.toLowerCase()] ?? 0;
}

export function meetsMinMembershipPlan(userPlan: string | null | undefined, minPlan: string | null | undefined): boolean {
    if (!minPlan?.trim()) return true;
    return membershipPlanRank(userPlan) >= membershipPlanRank(minPlan);
}

export function fetchMembershipPlans(fetchImpl?: typeof fetch) {
    return pilipalaRequest<{ plans: MembershipPlan[] }>("/api/v1/billing/membership-plans", { fetchImpl }).then((r) => r.plans ?? []);
}

export function fetchMembershipMe(fetchImpl?: typeof fetch) {
    return pilipalaRequest<MembershipMe | null>("/api/v1/billing/membership/me", { fetchImpl });
}

export function checkoutMembership(priceId: number, fetchImpl?: typeof fetch) {
    return pilipalaRequest<RechargeOrder>("/api/v1/billing/membership/checkout", {
        method: "POST",
        body: JSON.stringify({ price_id: priceId }),
        fetchImpl,
    });
}
