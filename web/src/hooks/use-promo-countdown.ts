import { useEffect, useState } from "react";

export type PromoCountdownParts = {
    days: number;
    hours: number;
    minutes: number;
    seconds: number;
    expired: boolean;
};

function partsFromRemaining(remainingMs: number): PromoCountdownParts {
    const total = Math.max(0, remainingMs);
    const days = Math.floor(total / 86_400_000);
    const hours = Math.floor((total % 86_400_000) / 3_600_000);
    const minutes = Math.floor((total % 3_600_000) / 60_000);
    const seconds = Math.floor((total % 60_000) / 1000);
    return { days, hours, minutes, seconds, expired: total <= 0 };
}

export function usePromoCountdown(endAtMs: number): PromoCountdownParts {
    const [now, setNow] = useState(() => Date.now());

    useEffect(() => {
        const tick = () => setNow(Date.now());
        tick();
        const id = window.setInterval(tick, 1000);
        return () => window.clearInterval(id);
    }, [endAtMs]);

    return partsFromRemaining(endAtMs - now);
}

export function formatCountdownUnit(value: number): string {
    return String(value).padStart(2, "0");
}
