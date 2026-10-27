import { pilipalaRequest } from "@/services/api/pilipala-client";

export type CreditProduct = {
    id: number | null;
    code: string;
    tab: string;
    name_zh: string;
    name_en: string;
    description_zh?: string | null;
    description_en?: string | null;
    price_fen: number;
    list_price_fen?: number | null;
    credits: number;
    bonus_credits: number;
    first_purchase_bonus_percent?: number | null;
    validity_days?: number | null;
    min_plan_code?: string | null;
    badge_zh?: string | null;
    badge_en?: string | null;
    meta_json?: string | null;
    sort_order: number;
};

export async function fetchCreditProducts(tab?: string): Promise<CreditProduct[]> {
    const query = tab ? `?tab=${encodeURIComponent(tab)}` : "";
    return pilipalaRequest<CreditProduct[]>(`/api/v1/billing/credit-products${query}`);
}
