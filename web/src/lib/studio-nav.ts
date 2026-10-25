import type { LucideIcon } from "lucide-react";
import { BookOpen, Bot, FileText, FolderOpen, Home, LayoutGrid, Maximize2, Puzzle } from "lucide-react";

export type StudioNavItem = {
    id: string;
    labelKey: string;
    href?: string;
    external?: boolean;
    icon: LucideIcon;
    badgeKey?: string;
    action?: "agent" | "plugins";
    match?: (pathname: string) => boolean;
};

export type StudioNavGroup = {
    labelKey: string;
    items: StudioNavItem[];
};

export const studioNavGroups: StudioNavGroup[] = [
    {
        labelKey: "studio.nav.browse",
        items: [
            { id: "home", labelKey: "studio.nav.home", href: "/", icon: Home, match: (p) => p === "/" },
            { id: "projects", labelKey: "studio.nav.projects", href: "/canvas", icon: LayoutGrid, match: (p) => p === "/canvas" },
            { id: "assets", labelKey: "studio.nav.assets", href: "/assets", icon: FolderOpen, match: (p) => p === "/assets" },
            { id: "plugins", labelKey: "studio.nav.plugins", icon: Puzzle, action: "plugins" },
        ],
    },
    {
        labelKey: "studio.nav.create",
        items: [
            { id: "canvas", labelKey: "studio.nav.pilipalaCanvas", href: "/canvas", icon: Maximize2, match: (p) => p === "/canvas" },
            { id: "agent", labelKey: "studio.nav.agent", icon: Bot, badgeKey: "studio.nav.agentBeta", action: "agent" },
        ],
    },
    {
        labelKey: "studio.nav.resources",
        items: [
            { id: "prompts", labelKey: "studio.nav.prompts", href: "/prompts", icon: FileText, match: (p) => p === "/prompts" },
            { id: "docs", labelKey: "studio.nav.docs", href: "docs", icon: BookOpen, external: true },
        ],
    },
];
