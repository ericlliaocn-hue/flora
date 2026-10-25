import { useState } from "react";
import { useTranslation } from "react-i18next";
import { X } from "lucide-react";

const STORAGE_KEY = "flora:studio-promo-dismissed:v1";

type StudioPromoStripProps = {
    onRecharge?: () => void;
};

export function StudioPromoStrip({ onRecharge }: StudioPromoStripProps) {
    const { t } = useTranslation();
    const [visible, setVisible] = useState(() => {
        try {
            return localStorage.getItem(STORAGE_KEY) !== "1";
        } catch {
            return true;
        }
    });

    if (!visible) return null;

    return (
        <div
            className="relative z-50 flex h-9 shrink-0 items-center justify-center gap-3 border-b border-white/[0.08] bg-gradient-to-r from-[#141820] via-[#1a1e28] to-[#16141f] px-10 text-xs text-neutral-400"
            role="region"
            aria-label={t("studio.promo.label")}
        >
            <div className="inline-flex items-center gap-1 font-semibold tabular-nums tracking-wide text-neutral-100" aria-hidden="true">
                <TimerBox value="02" />
                <span className="text-neutral-500">:</span>
                <TimerBox value="14" />
                <span className="text-neutral-500">:</span>
                <TimerBox value="36" />
                <span className="text-neutral-500">:</span>
                <TimerBox value="08" />
            </div>
            <p className="text-[12.5px] text-[#c8cdd6]">
                <strong className="font-semibold text-neutral-100">{t("studio.promo.title")}</strong>
                <span className="text-neutral-400"> · {t("studio.promo.copy")}</span>
            </p>
            <button
                type="button"
                className="h-6 rounded-full bg-[#e8e8e8] px-3 text-[12px] font-semibold text-[#0b0b0b] transition hover:bg-white"
                onClick={onRecharge}
            >
                {t("studio.promo.cta")}
            </button>
            <button
                type="button"
                className="absolute right-3.5 top-1/2 flex size-6 -translate-y-1/2 items-center justify-center rounded-md text-neutral-500 transition hover:bg-white/[0.06] hover:text-neutral-200"
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
    return (
        <span className="inline-grid min-w-[22px] place-items-center rounded-[5px] border border-white/10 bg-black/45 px-1 text-[12px]">
            {value}
        </span>
    );
}
