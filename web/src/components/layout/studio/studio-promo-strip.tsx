import { useState } from "react";
import { useTranslation } from "react-i18next";
import { X } from "lucide-react";

import { STUDIO_PROMO_END_MS } from "@/constant/studio-promo";
import { formatCountdownUnit, usePromoCountdown } from "@/hooks/use-promo-countdown";

const STORAGE_KEY = "flora:studio-promo-dismissed:v1";

type StudioPromoStripProps = {
    onRecharge?: () => void;
};

export function StudioPromoStrip({ onRecharge }: StudioPromoStripProps) {
    const { t } = useTranslation();
    const countdown = usePromoCountdown(STUDIO_PROMO_END_MS);
    const [visible, setVisible] = useState(() => {
        try {
            return localStorage.getItem(STORAGE_KEY) !== "1";
        } catch {
            return true;
        }
    });

    if (!visible || countdown.expired) return null;

    return (
        <div
            className="relative z-50 flex h-10 shrink-0 items-center justify-center gap-3 border-b border-[#1a6b6b]/40 bg-gradient-to-r from-[#0a2f2f] via-[#0d3d3d] to-[#0a2830] px-10 text-xs text-[#b8e8e4]"
            role="region"
            aria-label={t("studio.promo.label")}
        >
            <div
                className="inline-flex items-center gap-1 rounded-full border border-[#3d9a94]/35 bg-[#062424]/80 px-2.5 py-1 font-semibold tabular-nums tracking-wide text-white shadow-[inset_0_1px_0_rgba(255,255,255,0.06)]"
                aria-live="polite"
                aria-label={t("studio.promo.countdownLabel", {
                    days: countdown.days,
                    hours: countdown.hours,
                    minutes: countdown.minutes,
                    seconds: countdown.seconds,
                })}
            >
                <TimerBox value={formatCountdownUnit(countdown.days)} />
                <span className="text-[#5ec9c0]/80">:</span>
                <TimerBox value={formatCountdownUnit(countdown.hours)} />
                <span className="text-[#5ec9c0]/80">:</span>
                <TimerBox value={formatCountdownUnit(countdown.minutes)} />
                <span className="text-[#5ec9c0]/80">:</span>
                <TimerBox value={formatCountdownUnit(countdown.seconds)} />
            </div>
            <p className="hidden text-[12.5px] sm:block">
                <strong className="font-semibold text-white">{t("studio.promo.title")}</strong>
                <span className="text-[#9fd9d4]"> · {t("studio.promo.copy")}</span>
            </p>
            <button
                type="button"
                className="h-7 shrink-0 rounded-full bg-white px-3.5 text-[12px] font-semibold text-[#0a2f2f] transition hover:bg-[#e8fffc]"
                onClick={onRecharge}
            >
                {t("studio.promo.cta")}
            </button>
            <button
                type="button"
                className="absolute right-3 top-1/2 flex size-7 -translate-y-1/2 items-center justify-center rounded-full text-[#7ec9c3] transition hover:bg-white/10 hover:text-white"
                aria-label={t("studio.promo.dismiss")}
                onClick={() => {
                    setVisible(false);
                    try {
                        localStorage.setItem(STORAGE_KEY, "1");
                    } catch {
                        /* ignore */
                    }
                }}
            >
                <X className="size-4" />
            </button>
        </div>
    );
}

function TimerBox({ value }: { value: string }) {
    return <span className="min-w-[1.1rem] text-center text-[12px]">{value}</span>;
}
