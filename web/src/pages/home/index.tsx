import { Bot, ChevronRight, FileText, FolderOpen, ImageIcon, LayoutGrid, Plus, Search, Settings2, Sparkles, Video } from "lucide-react";
import { useEffect, useMemo, useState, type MouseEvent } from "react";
import { App, Image, Input } from "antd";
import { Link, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";

import { DOCS_URL } from "@/constant/env";
import { fetchPrompts, type Prompt } from "@/services/api/prompts";
import { previewUrlFor } from "@/services/image-storage";
import i18n from "@/i18n";
import { useCopyText } from "@/hooks/use-copy-text";
import { cn } from "@/lib/utils";
import { useAgentStore } from "@/stores/use-agent-store";
import { useCanvasStore, type CanvasProject } from "@/stores/canvas/use-canvas-store";
import { CanvasNodeType } from "@/types/canvas";

const SHOWCASE_CARD_ASPECT = "aspect-video";

type ToolTile = {
    key: string;
    label: string;
    icon: typeof LayoutGrid;
    href?: string;
    external?: boolean;
    onClick?: () => void;
};

const CAPABILITY_KEYS = ["canvas", "image", "video", "prompts", "agent"] as const;

export default function IndexPage() {
    const { message } = App.useApp();
    const { t, i18n: i18nInstance } = useTranslation();
    const navigate = useNavigate();
    const copyText = useCopyText();
    const toggleAgent = useAgentStore((state) => state.togglePanel);
    const projects = useCanvasStore((state) => state.projects);
    const [promptShowcase, setPromptShowcase] = useState<Prompt[]>([]);
    const [previewIndex, setPreviewIndex] = useState(0);
    const [previewOpen, setPreviewOpen] = useState(false);
    const [showcaseTab, setShowcaseTab] = useState("all");
    const [showcaseQuery, setShowcaseQuery] = useState("");

    useEffect(() => {
        void fetchPrompts({ pageSize: 24 })
            .then((data) => setPromptShowcase(data.items))
            .catch((error) => message.error(error instanceof Error ? error.message : i18n.t("home.promptError")));
    }, [message]);

    const toolTiles: ToolTile[] = [
        { key: "canvas", label: t("home.chips.canvas"), icon: LayoutGrid, href: "/canvas" },
        { key: "image", label: t("home.chips.image"), icon: ImageIcon, href: "/image" },
        { key: "video", label: t("home.chips.video"), icon: Video, href: "/video" },
        { key: "prompts", label: t("home.chips.prompts"), icon: FileText, href: "/prompts" },
        { key: "agent", label: t("home.chips.agent"), icon: Bot, onClick: toggleAgent },
        { key: "assets", label: t("studio.nav.assets"), icon: FolderOpen, href: "/assets" },
        { key: "config", label: t("navigation.config"), icon: Settings2, href: "/config" },
        { key: "docs", label: t("studio.nav.docs"), icon: Sparkles, href: DOCS_URL, external: true },
    ];

    const capabilityCards = [
        { key: "canvas", title: t("home.capabilities.canvas.title"), badge: t("home.capabilities.canvas.badge"), href: "/canvas" },
        { key: "image", title: t("home.capabilities.image.title"), badge: t("home.capabilities.image.badge"), href: "/image" },
        { key: "video", title: t("home.capabilities.video.title"), badge: t("home.capabilities.video.badge"), href: "/video" },
        { key: "prompts", title: t("home.capabilities.prompts.title"), badge: t("home.capabilities.prompts.badge"), href: "/prompts" },
        { key: "agent", title: t("home.capabilities.agent.title"), badge: t("home.capabilities.agent.badge"), onClick: toggleAgent },
    ] as const;

    const capabilityCovers = useMemo(() => {
        const map: Partial<Record<(typeof CAPABILITY_KEYS)[number], string>> = {};
        CAPABILITY_KEYS.forEach((key, index) => {
            const cover = promptShowcase[index]?.coverUrl;
            if (cover) map[key] = cover;
        });
        return map;
    }, [promptShowcase]);

    const recentProjects = useMemo(
        () => [...projects].sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()).slice(0, 8),
        [projects],
    );

    const showcaseTabs = useMemo(() => {
        const tags = new Set<string>();
        for (const item of promptShowcase) {
            for (const tag of item.tags.slice(0, 1)) tags.add(tag);
        }
        return ["all", ...Array.from(tags).slice(0, 5)];
    }, [promptShowcase]);

    const filteredShowcase = useMemo(() => {
        const q = showcaseQuery.trim().toLowerCase();
        return promptShowcase.filter((item) => {
            const tabOk = showcaseTab === "all" || item.tags.includes(showcaseTab);
            const queryOk = !q || item.title.toLowerCase().includes(q) || item.prompt.toLowerCase().includes(q);
            return tabOk && queryOk;
        });
    }, [promptShowcase, showcaseQuery, showcaseTab]);

    const openPreview = (item: Prompt) => {
        const globalIndex = promptShowcase.findIndex((p) => p.id === item.id);
        setPreviewIndex(globalIndex >= 0 ? globalIndex : 0);
        setPreviewOpen(true);
    };

    const usePromptInStudio = (item: Prompt) => {
        copyText(item.prompt, t("common.promptCopied"));
        navigate("/image");
    };

    return (
        <main className="h-full overflow-y-auto bg-[#0b0b0b] px-4 pb-14 pt-3 text-neutral-100 md:px-6 md:pt-4 lg:px-8">
            <div className="mx-auto w-full max-w-[1520px]">
                <CreateHero onCreate={() => navigate("/canvas")} label={t("home.createCanvas")} ariaLabel={t("home.hero.label")} />

                <div className="mt-5 grid grid-cols-4 gap-2 sm:grid-cols-8 sm:gap-3" aria-label={t("home.chips.label")}>
                    {toolTiles.map((tile) => {
                        const Icon = tile.icon;
                        const inner = (
                            <>
                                <span className="grid size-10 place-items-center rounded-full border border-white/[0.1] bg-[#141414] text-neutral-300 transition group-hover:border-white/[0.18] group-hover:bg-[#1a1a1a] group-hover:text-neutral-50">
                                    <Icon className="size-[18px]" strokeWidth={1.5} />
                                </span>
                                <span className="mt-1.5 line-clamp-2 text-center text-[11px] leading-tight text-neutral-500 transition group-hover:text-neutral-300">{tile.label}</span>
                            </>
                        );
                        const className = "group flex flex-col items-center";
                        if (tile.external && tile.href) {
                            return (
                                <a key={tile.key} href={tile.href} target="_blank" rel="noopener noreferrer" className={className}>
                                    {inner}
                                </a>
                            );
                        }
                        if (tile.href) {
                            return (
                                <Link key={tile.key} to={tile.href} className={className}>
                                    {inner}
                                </Link>
                            );
                        }
                        return (
                            <button key={tile.key} type="button" className={className} onClick={tile.onClick}>
                                {inner}
                            </button>
                        );
                    })}
                </div>

                <section className="mt-7" aria-labelledby="home-features-title">
                    <h2 id="home-features-title" className="mb-3 px-0.5 text-[15px] font-semibold tracking-tight text-neutral-50">{t("home.features.title")}</h2>
                    <div className="grid grid-cols-2 gap-2.5 sm:grid-cols-3 lg:grid-cols-5 lg:gap-3">
                        {capabilityCards.map((card) => {
                            const cover = capabilityCovers[card.key];
                            const showBadge = Boolean(card.badge?.trim());
                            const body = (
                                <article className="group flex h-full flex-col overflow-hidden rounded-[16px] border border-white/[0.08] bg-[#111] transition hover:border-white/[0.14]">
                                    <div className="relative aspect-video w-full overflow-hidden bg-[#1a1a1a]">
                                        {cover ? (
                                            <img src={cover} alt="" className="size-full object-cover transition duration-300 group-hover:scale-[1.02]" loading="lazy" />
                                        ) : (
                                            <div className="size-full bg-gradient-to-br from-[#1a2830] via-[#141820] to-[#101010]" />
                                        )}
                                    </div>
                                    <div className="flex items-center gap-2 px-2.5 py-2">
                                        <h3 className="min-w-0 flex-1 truncate text-[13px] font-medium text-neutral-100">{card.title}</h3>
                                        {showBadge ? (
                                            <span className="shrink-0 rounded-md bg-[#2d6cdf] px-1.5 py-0.5 text-[9px] font-semibold leading-none text-white">
                                                {card.badge}
                                            </span>
                                        ) : null}
                                    </div>
                                </article>
                            );
                            if ("href" in card && card.href) {
                                return (
                                    <Link key={card.key} to={card.href} className="block h-full">
                                        {body}
                                    </Link>
                                );
                            }
                            return (
                                <button key={card.key} type="button" className="h-full w-full text-left" onClick={card.onClick}>
                                    {body}
                                </button>
                            );
                        })}
                    </div>
                </section>

                <section className="mt-7" aria-labelledby="home-recent-title">
                    <div className="mb-2.5 flex items-center justify-between px-0.5">
                        <h2 id="home-recent-title" className="text-[15px] font-semibold tracking-tight text-neutral-50">{t("home.recent.title")}</h2>
                        <Link to="/canvas" className="inline-flex items-center gap-0.5 text-[12px] text-neutral-500 transition hover:text-neutral-200">
                            {t("home.recent.viewAll")}
                            <ChevronRight className="size-3.5" />
                        </Link>
                    </div>
                    {recentProjects.length ? (
                        <div className="flex gap-3 overflow-x-auto pb-1 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
                            {recentProjects.map((project) => (
                                <RecentProjectCard key={project.id} project={project} locale={i18nInstance.resolvedLanguage} />
                            ))}
                        </div>
                    ) : (
                        <div className="flex min-h-[72px] items-center justify-center rounded-[14px] border border-dashed border-white/[0.08] bg-[#0f0f0f] px-4 text-center text-[12px] text-neutral-500">
                            {t("home.recent.empty")}
                        </div>
                    )}
                </section>

                <section className="mt-8 pb-6" id="showcase" aria-labelledby="home-showcase-title">
                    <div className="sticky top-0 z-20 -mx-4 mb-4 border-b border-white/[0.06] bg-[#0b0b0b]/95 px-4 py-3 backdrop-blur-md md:-mx-6 md:px-6 lg:-mx-8 lg:px-8">
                        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                            <h2 id="home-showcase-title" className="text-[15px] font-semibold tracking-tight text-neutral-50">{t("home.showcaseTitle")}</h2>
                            <Input
                                allowClear
                                prefix={<Search className="size-3.5 text-neutral-500" />}
                                placeholder={t("home.showcaseSearch")}
                                value={showcaseQuery}
                                onChange={(event) => setShowcaseQuery(event.target.value)}
                                className="max-w-[220px] rounded-full border-white/[0.08] bg-[#121212] text-[12px]"
                            />
                        </div>
                        <div className="mt-3 flex flex-wrap gap-x-4 gap-y-2">
                            {showcaseTabs.map((tab) => {
                                const active = showcaseTab === tab;
                                return (
                                    <button
                                        key={tab}
                                        type="button"
                                        onClick={() => setShowcaseTab(tab)}
                                        className={cn(
                                            "relative text-[12px] transition",
                                            active ? "font-medium text-neutral-100" : "text-neutral-500 hover:text-neutral-300",
                                        )}
                                    >
                                        {tab === "all" ? (
                                            <span className="inline-flex items-center gap-1.5">
                                                {active ? <span className="size-1.5 rounded-full bg-[#e85d5d]" aria-hidden="true" /> : null}
                                                {t("home.showcaseTabAll")}
                                            </span>
                                        ) : (
                                            tab
                                        )}
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                    <div className="grid grid-cols-2 gap-3 md:grid-cols-4 md:gap-4">
                        {filteredShowcase.map((item) => (
                            <ShowcaseCard
                                key={item.id}
                                item={item}
                                meta={item.tags[0] ?? t("home.showcaseFallback")}
                                onOpen={() => openPreview(item)}
                                onUse={() => copyText(item.prompt, t("common.promptCopied"))}
                                onMakeSame={() => usePromptInStudio(item)}
                                useLabel={t("home.showcaseUseThis")}
                                makeSameLabel={t("home.showcaseMakeSame")}
                            />
                        ))}
                    </div>
                </section>

                <footer className="mt-2 border-t border-white/[0.07] px-1 pt-5">
                    <div className="flex flex-wrap items-center justify-between gap-4">
                        <p className="text-[12px] text-neutral-500">
                            <strong className="font-semibold text-neutral-200">pilipala</strong>
                            {t("home.footer.brand")}
                        </p>
                        <div className="flex flex-wrap gap-4 text-[12px] text-neutral-500">
                            <a href={DOCS_URL} target="_blank" rel="noopener noreferrer" className="hover:text-neutral-300">
                                {t("studio.nav.docs")}
                            </a>
                            <Link to="/config" className="hover:text-neutral-300">{t("navigation.config")}</Link>
                        </div>
                    </div>
                </footer>
            </div>

            <Image.PreviewGroup
                preview={{
                    open: previewOpen,
                    current: previewIndex,
                    onOpenChange: setPreviewOpen,
                    onChange: setPreviewIndex,
                }}
            >
                <div className="hidden">
                    {promptShowcase.map((item) => (
                        <Image key={item.id} src={item.coverUrl} alt={item.title} />
                    ))}
                </div>
            </Image.PreviewGroup>
        </main>
    );
}

function CreateHero({ onCreate, label, ariaLabel }: { onCreate: () => void; label: string; ariaLabel: string }) {
    const [spot, setSpot] = useState({ x: 50, y: 50 });

    const handleMove = (event: MouseEvent<HTMLElement>) => {
        const rect = event.currentTarget.getBoundingClientRect();
        setSpot({
            x: ((event.clientX - rect.left) / rect.width) * 100,
            y: ((event.clientY - rect.top) / rect.height) * 100,
        });
    };

    return (
        <section
            className="relative flex min-h-[132px] flex-col items-center justify-center overflow-hidden rounded-[16px] border border-dashed border-white/[0.1] bg-[#101010] px-4 py-5 md:min-h-[148px]"
            aria-label={ariaLabel}
            onMouseMove={handleMove}
        >
            <div
                className="pointer-events-none absolute inset-0 opacity-60 transition-opacity duration-300"
                style={{
                    background: `radial-gradient(ellipse 55% 80% at ${spot.x}% ${spot.y}%, rgba(94,201,192,0.09), transparent 65%)`,
                }}
            />
            <button
                type="button"
                className="group relative z-[1] flex flex-col items-center"
                onClick={onCreate}
                aria-label={label}
            >
                <span className="grid size-14 place-items-center rounded-full bg-white text-neutral-900 shadow-[0_8px_28px_rgba(0,0,0,0.35)] transition duration-200 group-hover:scale-[1.04] group-active:scale-[0.98]">
                    <Plus className="size-7" strokeWidth={2} />
                </span>
                <span className="mt-2.5 text-[14px] font-medium text-neutral-100">{label}</span>
            </button>
        </section>
    );
}

function ShowcaseCard({
    item,
    meta,
    onOpen,
    onUse,
    onMakeSame,
    useLabel,
    makeSameLabel,
}: {
    item: Prompt;
    meta: string;
    onOpen: () => void;
    onUse: () => void;
    onMakeSame: () => void;
    useLabel: string;
    makeSameLabel: string;
}) {
    return (
        <article className="group text-left">
            <div className="relative overflow-hidden rounded-[16px] border border-white/[0.08] bg-[#111]">
                <button type="button" className="block w-full" onClick={onOpen}>
                    <img
                        src={item.coverUrl}
                        alt={item.title}
                        className={cn("w-full object-cover bg-[#1a1a1a]", SHOWCASE_CARD_ASPECT)}
                        loading="lazy"
                    />
                </button>
                <div className="pointer-events-none absolute inset-0 bg-black/35 opacity-0 transition-opacity duration-200 group-hover:opacity-100" />
                <div className="absolute inset-x-0 bottom-0 flex justify-center gap-2 p-2.5 opacity-0 transition-opacity duration-200 group-hover:opacity-100">
                    <button
                        type="button"
                        className="pointer-events-auto h-7 rounded-full border border-white/20 bg-black/50 px-3 text-[11px] text-white backdrop-blur-sm transition hover:bg-black/70"
                        onClick={(event) => {
                            event.stopPropagation();
                            onUse();
                        }}
                    >
                        {useLabel}
                    </button>
                    <button
                        type="button"
                        className="studio-solid-on-white pointer-events-auto h-7 rounded-full bg-white px-3 text-[11px] font-medium transition hover:bg-neutral-100"
                        onClick={(event) => {
                            event.stopPropagation();
                            onMakeSame();
                        }}
                    >
                        {makeSameLabel}
                    </button>
                </div>
            </div>
            <div className="mt-1.5 px-0.5">
                <h4 className="truncate text-[13px] font-medium leading-tight text-neutral-100">{item.title}</h4>
                <p className="mt-0.5 truncate text-[11px] leading-tight text-neutral-500">{meta}</p>
            </div>
        </article>
    );
}

function projectPreviewUrl(project: CanvasProject): string | null {
    for (const node of project.nodes) {
        if (node.type === CanvasNodeType.Image) {
            const fromStorage = previewUrlFor(node.metadata?.storageKey);
            if (fromStorage) return fromStorage;
            const content = node.metadata?.content;
            if (content?.startsWith("http") || content?.startsWith("data:") || content?.startsWith("blob:")) return content;
            const firstImage = node.metadata?.images?.[0];
            if (firstImage) {
                const thumb = previewUrlFor(firstImage.storageKey);
                if (thumb) return thumb;
                if (firstImage.content) return firstImage.content;
            }
        }
        if (node.type === CanvasNodeType.Video && node.metadata?.content) {
            return null;
        }
    }
    return null;
}

function RecentProjectCard({ project, locale }: { project: CanvasProject; locale?: string }) {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const thumb = projectPreviewUrl(project);
    const dateLabel = new Date(project.updatedAt).toLocaleDateString(locale, { month: "2-digit", day: "2-digit" });

    return (
        <button
            type="button"
            onClick={() => navigate(`/canvas/${project.id}`)}
            className="flex w-[min(100%,280px)] shrink-0 items-stretch overflow-hidden rounded-[14px] border border-white/[0.08] bg-[#111] text-left transition hover:border-white/[0.14]"
        >
            <div className="relative w-[100px] shrink-0 bg-[#1a1a1a]">
                {thumb ? (
                    <img src={thumb} alt="" className="absolute inset-0 size-full object-cover" />
                ) : (
                    <div className="flex size-full min-h-[76px] items-center justify-center text-neutral-600">
                        <LayoutGrid className="size-5 opacity-50" />
                    </div>
                )}
            </div>
            <div className="flex min-w-0 flex-1 flex-col justify-center px-3 py-2">
                <p className="truncate text-[13px] font-medium text-neutral-100">{project.title || t("home.recent.untitled")}</p>
                <p className="mt-0.5 text-[11px] text-neutral-500">{t("home.recent.updated", { date: dateLabel })}</p>
            </div>
        </button>
    );
}
