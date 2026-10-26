import { useState } from "react";
import { useTranslation } from "react-i18next";
import { X } from "lucide-react";

import { useStudioFeatures } from "@/components/layout/studio/use-studio-features";
import { formatCountdownUnit, usePromoCountdown } from "@/hooks/use-promo-countdown";
import { parsePromoEndMs, shouldShowStudioPromo } from "@/services/api/studio-features";

const STORAGE_KEY = "pilipala:studio-promo-dismissed:v1";

type StudioPromoStripProps = {
    onRecharge?: () => void;
};

export function StudioPromoStrip({ onRecharge }: StudioPromoStripProps) {
    const { t } = useTranslation();
    const features = useStudioFeatures();
    const countdown = usePromoCountdown(parsePromoEndMs(features.promo_end_at));
    const [visible, setVisible] = useState(() => {
        try {
            return localStorage.getItem(STORAGE_KEY) !== "1";
        } catch {
            return true;
        }
    });

    if (
        !shouldShowStudioPromo({
            promoEnabled: features.promo_enabled,
            endAtMs: parsePromoEndMs(features.promo_end_at),
            nowMs: Date.now(),
            dismissed: !visible,
        }) ||
        countdown.expired
    ) {
        return null;
    }

    return (
        <div
            className="relative z-50 flex h-10 shrink-0 items-center justify-center gap-3 bg-[#0e7a78] px-10 text-xs text-white"
            role="region"
            aria-label={t("studio.promo.label")}
        >
            <div
                className="inline-flex items-center gap-1"
                aria-live="polite"
                aria-label={t("studio.promo.countdownLabel", {
                    days: countdown.days,
                    hours: countdown.hours,
                    minutes: countdown.minutes,
                    seconds: countdown.seconds,
                })}
            >
                <TimerUnit value={formatCountdownUnit(countdown.days)} />
                <Colon />
                <TimerUnit value={formatCountdownUnit(countdown.hours)} />
                <Colon />
                <TimerUnit value={formatCountdownUnit(countdown.minutes)} />
                <Colon />
                <TimerUnit value={formatCountdownUnit(countdown.seconds)} />
            </div>
            <p className="hidden text-[12.5px] sm:block">
                <strong className="font-semibold text-white">{features.promo_title}</strong>
                <span className="text-white/85"> · {features.promo_copy}</span>
            </p>
            <button
                type="button"
                className="studio-solid-on-white h-7 shrink-0 rounded-md bg-white px-3.5 text-xs font-semibold transition hover:bg-[#e8fffc]"
                onClick={onRecharge}
            >
                {features.promo_cta}
            </button>
            <button
                type="button"
                className="absolute right-3 top-1/2 flex size-7 -translate-y-1/2 items-center justify-center rounded-full text-white/80 transition hover:bg-white/10 hover:text-white"
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

function TimerUnit({ value }: { value: string }) {
    return (
        <span className="inline-flex items-center gap-0.5">
            <DigitBox digit={value[0] ?? "0"} />
            <DigitBox digit={value[1] ?? "0"} />
        </span>
    );
}

function DigitBox({ digit }: { digit: string }) {
    return (
        <span className="inline-flex h-[22px] min-w-[18px] items-center justify-center rounded-[4px] bg-white px-1 text-[12px] font-semibold tabular-nums text-black">
            {digit}
        </span>
    );
}

function Colon() {
    return <span className="px-0.5 text-[12px] font-semibold text-white/70">:</span>;
}
