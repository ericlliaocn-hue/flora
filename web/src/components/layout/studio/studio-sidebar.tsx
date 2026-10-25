import { Link, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";

import { PilipalaLogo } from "@/components/brand/pilipala-logo";
import { APP_VERSION, DOCS_URL } from "@/constant/env";
import { studioNavGroups, type StudioNavItem } from "@/lib/studio-nav";
import { cn } from "@/lib/utils";

type StudioSidebarProps = {
    onAgent: () => void;
    onPlugins: () => void;
    className?: string;
};

export function StudioSidebar({ onAgent, onPlugins, className }: StudioSidebarProps) {
    const { t } = useTranslation();
    const { pathname } = useLocation();

    const renderItem = (item: StudioNavItem) => {
        const Icon = item.icon;
        const active = item.match?.(pathname) ?? false;
        const baseClass = cn(
            "flex h-9 w-full items-center gap-2.5 rounded-[10px] px-2.5 text-[13px] text-neutral-400 transition",
            active ? "bg-white/[0.08] font-medium text-neutral-100" : "hover:bg-white/[0.04] hover:text-neutral-200",
        );

        const content = (
            <>
                <span className="grid size-[18px] shrink-0 place-items-center opacity-85">
                    <Icon className="size-4" strokeWidth={1.6} />
                </span>
                <span className="truncate">{t(item.labelKey)}</span>
                {item.badgeKey ? (
                    <span className="ml-auto rounded-full border border-white/[0.08] bg-white/[0.06] px-1.5 py-0.5 text-[9px] font-semibold tracking-wide text-neutral-400">
                        {t(item.badgeKey)}
                    </span>
                ) : null}
            </>
        );

        if (item.action === "agent") {
            return (
                <button key={item.id} type="button" className={cn(baseClass, "text-left")} onClick={onAgent}>
                    {content}
                </button>
            );
        }
        if (item.action === "plugins") {
            return (
                <button key={item.id} type="button" className={cn(baseClass, "text-left")} onClick={onPlugins}>
                    {content}
                </button>
            );
        }
        if (item.external) {
            return (
                <a key={item.id} href={DOCS_URL} target="_blank" rel="noopener noreferrer" className={baseClass}>
                    {content}
                </a>
            );
        }
        return (
            <Link key={item.id} to={item.href ?? "/"} className={baseClass}>
                {content}
            </Link>
        );
    };

    return (
        <aside
            className={cn(
                "hidden w-[240px] shrink-0 flex-col border-r border-white/[0.07] bg-[#0e0e0e] px-3 pb-4 pt-3 md:flex",
                className,
            )}
            aria-label={t("studio.sidebar.label")}
        >
            <Link to="/" className="mb-3 flex items-center gap-2.5 px-2 py-1">
                <PilipalaLogo variant="mark" className="h-8 w-11 shrink-0" />
                <span className="min-w-0 text-[16px] font-semibold leading-tight tracking-tight text-neutral-100">
                    pilipala
                    <small className="mt-0.5 block text-[10.5px] font-normal text-neutral-500">{t("studio.sidebar.tagline")}</small>
                </span>
            </Link>

            <nav className="flex min-h-0 flex-1 flex-col gap-0.5 overflow-y-auto py-0.5">
                {studioNavGroups.map((group) => (
                    <div key={group.labelKey}>
                        <div className="mb-1 mt-3 px-2.5 text-[11px] font-medium text-neutral-600 first:mt-0">{t(group.labelKey)}</div>
                        {group.items.map(renderItem)}
                    </div>
                ))}
            </nav>

            <div className="mt-2 space-y-2 border-t border-white/[0.07] px-2 pt-3">
                <p className="text-[10.5px] tabular-nums text-neutral-600">{t("studio.sidebar.version", { version: APP_VERSION })}</p>
                <div className="rounded-[10px] border border-[#1a6b6b]/20 bg-[#0a2228]/50 px-2.5 py-2 text-[11px] leading-snug text-[#8fbfb9]">
                    {t("studio.sidebar.prepaidTip")}
                </div>
            </div>
        </aside>
    );
}
