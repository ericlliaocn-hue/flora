import { Drawer } from "antd";
import { Link, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";

import { DOCS_URL } from "@/constant/env";
import { navigationTools } from "@/constant/navigation-tools";
import { studioNavGroups, type StudioNavItem } from "@/lib/studio-nav";
import { cn } from "@/lib/utils";

type StudioMobileDrawerProps = {
    open: boolean;
    onClose: () => void;
    onAgent: () => void;
    onPlugins: () => void;
};

export function StudioMobileDrawer({ open, onClose, onAgent, onPlugins }: StudioMobileDrawerProps) {
    const { t } = useTranslation();
    const { pathname } = useLocation();
    const slug = pathname.split("/").filter(Boolean)[0];

    const renderStudioItem = (item: StudioNavItem) => {
        const Icon = item.icon;
        const active = item.match?.(pathname) ?? false;
        const className = cn(
            "flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-base transition",
            active ? "bg-white/[0.08] font-medium text-neutral-100" : "text-neutral-400 hover:bg-white/[0.05] hover:text-neutral-100",
        );

        if (item.action === "agent") {
            return (
                <button key={item.id} type="button" className={className} onClick={() => { onAgent(); onClose(); }}>
                    <Icon className="size-5" />
                    <span>{t(item.labelKey)}</span>
                </button>
            );
        }
        if (item.action === "plugins") {
            return (
                <button key={item.id} type="button" className={className} onClick={onPlugins}>
                    <Icon className="size-5" />
                    <span>{t(item.labelKey)}</span>
                </button>
            );
        }
        if (item.external) {
            return (
                <a key={item.id} href={DOCS_URL} target="_blank" rel="noopener noreferrer" className={className} onClick={onClose}>
                    <Icon className="size-5" />
                    <span>{t(item.labelKey)}</span>
                </a>
            );
        }
        return (
            <Link key={item.id} to={item.href ?? "/"} className={className} onClick={onClose}>
                <Icon className="size-5" />
                <span>{t(item.labelKey)}</span>
            </Link>
        );
    };

    return (
        <Drawer title={t("topNav.navigation")} placement="left" size={300} open={open} onClose={onClose} className="md:hidden">
            <div className="space-y-4">
                {studioNavGroups.map((group) => (
                    <div key={group.labelKey}>
                        <div className="mb-2 px-1 text-xs font-medium uppercase tracking-wide text-neutral-500">{t(group.labelKey)}</div>
                        <div className="space-y-1">{group.items.map(renderStudioItem)}</div>
                    </div>
                ))}
                <div>
                    <div className="mb-2 px-1 text-xs font-medium uppercase tracking-wide text-neutral-500">{t("studio.nav.workbenches")}</div>
                    <div className="space-y-1">
                        {navigationTools.map((tool) => {
                            const Icon = tool.icon;
                            const active = tool.slug === slug;
                            return (
                                <Link
                                    key={tool.slug}
                                    to={`/${tool.slug}`}
                                    onClick={onClose}
                                    className={cn(
                                        "flex items-center gap-3 rounded-lg px-3 py-2.5 text-base transition",
                                        active ? "bg-white/[0.08] font-medium text-neutral-100" : "text-neutral-400 hover:bg-white/[0.05]",
                                    )}
                                >
                                    <Icon className="size-5" />
                                    <span>{t(`navigation.${tool.slug}`)}</span>
                                </Link>
                            );
                        })}
                    </div>
                </div>
            </div>
        </Drawer>
    );
}
