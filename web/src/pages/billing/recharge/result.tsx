import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";

import { fetchRechargeOrder } from "@/services/api/flora-recharge";

const POLL_MS = 3000;

export default function RechargeResultPage() {
    const [params] = useSearchParams();
    const outTradeNo = params.get("out_trade_no") ?? params.get("outTradeNo") ?? "";
    const [status, setStatus] = useState<string>("pending_pay");
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (!outTradeNo) {
            setError("缺少订单号");
            return;
        }
        let active = true;
        const tick = async () => {
            try {
                const order = await fetchRechargeOrder(outTradeNo);
                if (!active) {
                    return;
                }
                setStatus(order.status);
                if (order.status === "paid" || order.status === "failed" || order.status === "closed") {
                    return;
                }
                window.setTimeout(tick, POLL_MS);
            } catch {
                if (active) {
                    setError("查询订单失败，请稍后刷新");
                }
            }
        };
        void tick();
        return () => {
            active = false;
        };
    }, [outTradeNo]);

    const label =
        status === "paid"
            ? "充值成功"
            : status === "failed" || status === "closed"
              ? "充值未完成"
              : "支付处理中，请稍候…";

    return (
        <div className="mx-auto max-w-md px-4 py-16 text-center">
            <h1 className="mb-4 text-xl font-medium">充值结果</h1>
            {error ? <p className="text-red-600">{error}</p> : <p>{label}</p>}
            {outTradeNo ? <p className="mt-4 text-sm text-neutral-500">订单号：{outTradeNo}</p> : null}
        </div>
    );
}
