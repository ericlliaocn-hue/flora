import { Link, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";

import { DOCS_URL } from "@/constant/env";
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
            "flex h-9 items-center gap-2.5 rounded-[9px] px-2.5 text-[13.5px] text-neutral-400 transition",
            active ? "bg-white/[0.07] font-medium text-neutral-100" : "hover:bg-white/[0.04] hover:text-neutral-100",
        );

        const content = (
            <>
                <span className="grid size-[18px] shrink-0 place-items-center opacity-90">
                    <Icon className="size-4" strokeWidth={1.6} />
                </span>
                <span className="truncate">{t(item.labelKey)}</span>
                {item.badgeKey ? (
                    <span className="ml-auto rounded-full border border-white/[0.08] bg-white/[0.08] px-1.5 py-0.5 text-[9.5px] font-semibold tracking-wide text-neutral-400">
                        {t(item.badgeKey)}
                    </span>
                ) : null}
            </>
        );

        if (item.action === "agent") {
            return (
                <button key={item.id} type="button" className={cn(baseClass, "w-full text-left")} onClick={onAgent}>
                    {content}
                </button>
            );
        }
        if (item.action === "plugins") {
            return (
                <button key={item.id} type="button" className={cn(baseClass, "w-full text-left")} onClick={onPlugins}>
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
                "hidden w-[212px] shrink-0 flex-col border-r border-white/[0.08] bg-[#0e0e0e] px-3 pb-4 pt-3.5 md:flex",
                className,
            )}
            aria-label={t("studio.sidebar.label")}
        >
            <Link to="/" className="mb-4 flex items-center gap-2.5 px-2 py-1.5">
                <span
                    className="grid size-7 place-items-center rounded-lg border border-white/[0.14] bg-gradient-to-br from-[#2a2a2a] to-[#141414] text-[13px] font-bold tracking-tight text-neutral-100"
                    aria-hidden="true"
                >
                    f
                </span>
                <span className="text-[17px] font-semibold tracking-tight text-neutral-100">
                    flora
                    <small className="mt-0.5 block text-[10.5px] font-normal tracking-wide text-neutral-500">{t("studio.sidebar.tagline")}</small>
                </span>
            </Link>

            <nav className="flex min-h-0 flex-1 flex-col gap-0.5 overflow-y-auto py-1">
                {studioNavGroups.map((group) => (
                    <div key={group.labelKey}>
                        <div className="mb-1 mt-3.5 px-2.5 text-[11px] font-medium uppercase tracking-[0.06em] text-neutral-500 first:mt-1">{t(group.labelKey)}</div>
                        {group.items.map(renderItem)}
                    </div>
                ))}
            </nav>

            <div className="mt-2 border-t border-white/[0.08] px-2.5 pt-3 text-[11.5px] leading-snug text-neutral-500">
                <strong className="font-medium text-neutral-400">flora</strong>
                <br />
                {t("studio.sidebar.foot")}
            </div>
        </aside>
    );
}
