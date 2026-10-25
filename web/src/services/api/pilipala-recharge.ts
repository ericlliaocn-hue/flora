import { pilipalaRequest } from "@/services/api/pilipala-client";

export type RechargePackage = {
    id: string;
    money_fen: number;
    credits: number;
    name: string;
};

export type RechargeOrder = {
    id: string;
    out_trade_no: string;
    status: string;
    money_fen: number;
    credits: number;
    pay_url?: string;
    qrcode_url?: string;
    expire_at?: string;
};

export async function fetchRechargePackages(): Promise<RechargePackage[]> {
    return pilipalaRequest<RechargePackage[]>("/api/v1/billing/recharge/packages");
}

export async function createRechargeOrder(packageId: string, device?: string): Promise<RechargeOrder> {
    return pilipalaRequest<RechargeOrder>("/api/v1/billing/recharge/orders", {
        method: "POST",
        body: JSON.stringify({ package_id: packageId, device }),
    });
}

export async function fetchRechargeOrder(outTradeNo: string): Promise<RechargeOrder> {
    return pilipalaRequest<RechargeOrder>(`/api/v1/billing/recharge/orders/${encodeURIComponent(outTradeNo)}`);
}
