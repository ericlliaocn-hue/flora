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

export type CreateRechargeOrderInput = {
    packageId?: string;
    productCode?: string;
    credits?: number;
    device?: string;
};

export async function createRechargeOrder(input: string | CreateRechargeOrderInput): Promise<RechargeOrder> {
    const body =
        typeof input === "string"
            ? { package_id: input }
            : {
                  package_id: input.packageId,
                  product_code: input.productCode,
                  credits: input.credits,
                  device: input.device,
              };
    return pilipalaRequest<RechargeOrder>("/api/v1/billing/recharge/orders", {
        method: "POST",
        body: JSON.stringify(body),
    });
}

export async function fetchRechargeOrder(outTradeNo: string): Promise<RechargeOrder> {
    return pilipalaRequest<RechargeOrder>(`/api/v1/billing/recharge/orders/${encodeURIComponent(outTradeNo)}`);
}
