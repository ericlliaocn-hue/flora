import { Bot, ChevronRight, FileText, FolderOpen, ImageIcon, LayoutGrid, Plus, Search, Settings2, Sparkles, Video } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { App, Image, Input } from "antd";
import { Link, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";

import { DOCS_URL } from "@/constant/env";
import { fetchPrompts, type Prompt } from "@/services/api/prompts";
import { previewUrlFor } from "@/services/image-storage";
import i18n from "@/i18n";
import { cn } from "@/lib/utils";
import { useAgentStore } from "@/stores/use-agent-store";
import { useCanvasStore, type CanvasProject } from "@/stores/canvas/use-canvas-store";
import { CanvasNodeType } from "@/types/canvas";

const showcaseAspects = ["aspect-[16/10]", "aspect-[5/4]", "aspect-[4/3]", "aspect-[3/2]", "aspect-video", "aspect-[16/9]"] as const;

const capabilityGradients = [
    "from-[#1a2a3a] via-[#243550] to-[#1a2838]",
    "from-[#2a1f28] via-[#3a2840] to-[#1e2830]",
    "from-[#1a2830] via-[#1f3a38] to-[#2a2438]",
    "from-[#28241a] via-[#303828] to-[#1a2a30]",
    "from-[#1e2238] via-[#2a1f40] to-[#182830]",
] as const;

type ToolTile = {
    key: string;
    label: string;
    icon: typeof LayoutGrid;
    href?: string;
    external?: boolean;
    onClick?: () => void;
};

export default function IndexPage() {
    const { message } = App.useApp();
    const { t, i18n: i18nInstance } = useTranslation();
    const navigate = useNavigate();
    const toggleAgent = useAgentStore((state) => state.togglePanel);
    const projects = useCanvasStore((state) => state.projects);
    const [promptShowcase, setPromptShowcase] = useState<Prompt[]>([]);
    const [previewIndex, setPreviewIndex] = useState(0);
    const [previewOpen, setPreviewOpen] = useState(false);
    const [showcaseTab, setShowcaseTab] = useState("all");
    const [showcaseQuery, setShowcaseQuery] = useState("");

    useEffect(() => {
        void fetchPrompts({ pageSize: 16 })
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
        { key: "canvas", title: t("home.capabilities.canvas.title"), body: t("home.capabilities.canvas.body"), href: "/canvas", gradient: capabilityGradients[0] },
        { key: "image", title: t("home.capabilities.image.title"), body: t("home.capabilities.image.body"), href: "/image", gradient: capabilityGradients[1] },
        { key: "video", title: t("home.capabilities.video.title"), body: t("home.capabilities.video.body"), href: "/video", gradient: capabilityGradients[2] },
        { key: "prompts", title: t("home.capabilities.prompts.title"), body: t("home.capabilities.prompts.body"), href: "/prompts", gradient: capabilityGradients[3] },
        { key: "agent", title: t("home.capabilities.agent.title"), body: t("home.capabilities.agent.body"), onClick: toggleAgent, gradient: capabilityGradients[4] },
    ] as const;

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

    return (
        <main className="h-full overflow-y-auto bg-[#0b0b0b] px-4 pb-16 pt-5 text-neutral-100 md:px-8 md:pt-7 lg:px-10">
            <div className="mx-auto w-full max-w-[1520px]">
                <section
                    className="relative flex min-h-[440px] flex-col items-center justify-center overflow-hidden rounded-[20px] border border-white/[0.08] bg-[#121212] px-6 py-12 md:min-h-[500px] md:px-10 md:py-14"
                    aria-label={t("home.hero.label")}
                >
                    <div
                        className="pointer-events-none absolute inset-0 opacity-40"
                        style={{
                            backgroundImage: "radial-gradient(circle, rgba(255,255,255,0.11) 1px, transparent 1px)",
                            backgroundSize: "20px 20px",
                            maskImage: "radial-gradient(ellipse 75% 70% at 50% 48%, black 15%, transparent 78%)",
                        }}
                    />
                    <div className="pointer-events-none absolute inset-5 rounded-[14px] border border-white/[0.05]" aria-hidden="true" />
                    <p className="relative z-[1] mb-8 text-[12px] uppercase tracking-[0.18em] text-neutral-500">{t("home.hero.kicker")}</p>
                    <div className="relative z-[1] flex flex-col items-center">
                        <button
                            type="button"
                            className="grid size-[88px] place-items-center rounded-[18px] bg-[#f2f2f2] text-[#0b0b0b] shadow-[0_0_0_1px_rgba(255,255,255,0.12),0_0_0_10px_rgba(255,255,255,0.03),0_20px_50px_rgba(0,0,0,0.55)] transition hover:bg-white active:scale-[0.98]"
                            onClick={() => navigate("/canvas")}
                            aria-label={t("home.createCanvas")}
                        >
                            <Plus className="size-9" strokeWidth={1.5} />
                        </button>
                        <p className="mt-4 text-[17px] font-semibold tracking-tight text-neutral-100">{t("home.createCanvas")}</p>
                    </div>
                    <p className="relative z-[1] mt-5 max-w-[520px] text-center text-[15px] leading-relaxed text-neutral-400">{t("home.hero.subtitle")}</p>
                    <p className="relative z-[1] mt-6 text-[12.5px] tracking-wide text-neutral-500">
                        <strong className="font-medium text-neutral-400">flora</strong>
                        <span className="text-neutral-600"> · </span>
                        {t("home.hero.mark")}
                    </p>
                </section>

                <div className="mt-10 mb-14 grid grid-cols-4 gap-3 sm:grid-cols-8 md:mt-12 md:gap-4" aria-label={t("home.chips.label")}>
                    {toolTiles.map((tile) => {
                        const Icon = tile.icon;
                        const inner = (
                            <>
                                <span className="grid size-11 place-items-center rounded-[12px] border border-white/[0.08] bg-[#161616] text-neutral-300 transition group-hover:border-white/[0.16] group-hover:bg-[#1c1c1c] group-hover:text-neutral-100">
                                    <Icon className="size-5" strokeWidth={1.5} />
                                </span>
                                <span className="mt-2 line-clamp-2 text-center text-[11px] leading-tight text-neutral-500 transition group-hover:text-neutral-300">{tile.label}</span>
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

                <section className="mb-14" aria-labelledby="home-recent-title">
                    <div className="mb-5 flex items-center justify-between px-0.5">
                        <h2 id="home-recent-title" className="text-[15px] font-semibold tracking-tight">{t("home.recent.title")}</h2>
                        <Link to="/canvas" className="inline-flex items-center gap-0.5 text-[12.5px] text-neutral-500 transition hover:text-neutral-200">
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
                        <div className="flex min-h-[120px] items-center justify-center rounded-[14px] border border-dashed border-white/[0.1] bg-[#101010] px-4 text-center text-[13px] text-neutral-500">
                            {t("home.recent.empty")}
                        </div>
                    )}
                </section>

                <section className="mb-14">
                    <div className="mb-5 flex items-baseline justify-between px-0.5">
                        <h2 className="text-[15px] font-semibold tracking-tight">{t("home.features.title")}</h2>
                        <span className="text-[12px] text-neutral-500">{t("home.features.hint")}</span>
                    </div>
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
                        {capabilityCards.map((card) => {
                            const body = (
                                <article className="group flex h-full min-h-[200px] flex-col overflow-hidden rounded-[14px] border border-white/[0.08] bg-[#141414] transition hover:-translate-y-0.5 hover:border-white/[0.14]">
                                    <div className={cn("relative h-[108px] bg-gradient-to-br", card.gradient)} aria-hidden="true">
                                        <CapabilityArt variant={card.key} />
                                        <div className="absolute inset-0 bg-gradient-to-t from-[#141414] via-transparent to-transparent" />
                                    </div>
                                    <div className="flex flex-1 flex-col gap-1 px-3.5 pb-4 pt-3">
                                        <h3 className="text-[13.5px] font-semibold text-neutral-100">{card.title}</h3>
                                        <p className="text-[12px] leading-relaxed text-neutral-500">{card.body}</p>
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

                <section className="mb-14" id="showcase" aria-labelledby="home-showcase-title">
                    <div className="mb-5 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                            <h2 id="home-showcase-title" className="text-[15px] font-semibold tracking-tight">{t("home.showcaseTitle")}</h2>
                            <p className="mt-0.5 text-[12px] text-neutral-500">{t("home.showcaseHint")}</p>
                        </div>
                        <Input
                            allowClear
                            prefix={<Search className="size-3.5 text-neutral-500" />}
                            placeholder={t("home.showcaseSearch")}
                            value={showcaseQuery}
                            onChange={(event) => setShowcaseQuery(event.target.value)}
                            className="max-w-xs rounded-full border-white/[0.1] bg-[#141414] text-[13px]"
                        />
                    </div>
                    <div className="mb-5 flex flex-wrap gap-2">
                        {showcaseTabs.map((tab) => (
                            <button
                                key={tab}
                                type="button"
                                onClick={() => setShowcaseTab(tab)}
                                className={cn(
                                    "h-8 rounded-full px-3 text-[12px] transition",
                                    showcaseTab === tab ? "bg-white/[0.12] font-medium text-neutral-100" : "text-neutral-500 hover:bg-white/[0.05] hover:text-neutral-300",
                                )}
                            >
                                {tab === "all" ? t("home.showcaseTabAll") : tab}
                            </button>
                        ))}
                    </div>
                    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
                        {filteredShowcase.map((item, index) => (
                            <button
                                key={item.id}
                                type="button"
                                onClick={() => {
                                    const globalIndex = promptShowcase.findIndex((p) => p.id === item.id);
                                    setPreviewIndex(globalIndex >= 0 ? globalIndex : index);
                                    setPreviewOpen(true);
                                }}
                                className="group overflow-hidden rounded-[12px] border border-white/[0.08] bg-[#141414] text-left transition hover:border-white/[0.14] hover:shadow-[0_8px_24px_rgba(0,0,0,0.35)]"
                            >
                                <img
                                    src={item.coverUrl}
                                    alt={item.title}
                                    className={cn("w-full object-cover bg-[#1a1a1a] transition duration-300 group-hover:scale-[1.02]", showcaseAspects[index % showcaseAspects.length])}
                                    loading="lazy"
                                />
                                <div className="px-2.5 py-2">
                                    <h4 className="truncate text-[12.5px] font-medium text-neutral-100">{item.title}</h4>
                                    <p className="mt-0.5 truncate text-[11px] text-neutral-500">{item.tags[0] ?? t("home.showcaseFallback")}</p>
                                </div>
                            </button>
                        ))}
                    </div>
                </section>

                <footer className="mt-2 border-t border-white/[0.08] px-1 pt-6">
                    <div className="flex flex-wrap items-center justify-between gap-4">
                        <p className="text-[13px] text-neutral-400">
                            <strong className="font-semibold text-neutral-100">flora</strong>
                            {t("home.footer.brand")}
                        </p>
                        <div className="flex flex-wrap gap-4 text-[12.5px] text-neutral-500">
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
            className="flex w-[min(100%,280px)] shrink-0 items-stretch overflow-hidden rounded-[14px] border border-white/[0.08] bg-[#141414] text-left transition hover:border-white/[0.14]"
        >
            <div className="relative w-[108px] shrink-0 bg-[#1a1a1a]">
                {thumb ? (
                    <img src={thumb} alt="" className="absolute inset-0 size-full object-cover" />
                ) : (
                    <div className="flex size-full min-h-[88px] items-center justify-center text-neutral-600">
                        <LayoutGrid className="size-6 opacity-50" />
                    </div>
                )}
            </div>
            <div className="flex min-w-0 flex-1 flex-col justify-center px-3 py-2.5">
                <p className="truncate text-[13.5px] font-medium text-neutral-100">{project.title || t("home.recent.untitled")}</p>
                <p className="mt-1 text-[11.5px] text-neutral-500">{t("home.recent.updated", { date: dateLabel })}</p>
            </div>
        </button>
    );
}

function CapabilityArt({ variant }: { variant: string }) {
    if (variant === "canvas") {
        return (
            <>
                <span className="absolute left-[16%] top-[30%] size-10 rounded-lg border border-white/15 bg-white/[0.08]" />
                <span className="absolute left-[40%] top-[22%] h-9 w-12 rounded-lg border border-white/15 bg-white/[0.06]" />
                <span className="absolute left-[58%] top-[40%] h-7 w-9 rounded-lg border border-white/15 bg-white/[0.06]" />
            </>
        );
    }
    if (variant === "image") {
        return <span className="absolute left-1/2 top-1/2 size-14 -translate-x-1/2 -translate-y-1/2 rounded-xl border border-white/20 bg-gradient-to-br from-white/20 to-white/5" />;
    }
    if (variant === "video") {
        return (
            <div className="absolute inset-0 flex items-center justify-center">
                <span className="h-10 w-16 rounded-lg border border-white/15 bg-white/[0.08]" />
            </div>
        );
    }
    if (variant === "prompts") {
        return (
            <div className="absolute left-[18%] right-[18%] top-[38%] space-y-1.5">
                <span className="block h-1.5 rounded bg-white/25" />
                <span className="block h-1.5 w-[80%] rounded bg-white/15" />
                <span className="block h-1.5 w-[60%] rounded bg-white/10" />
            </div>
        );
    }
    return (
        <div className="absolute inset-0 flex items-center justify-center">
            <Bot className="size-9 text-white/35" strokeWidth={1.4} />
        </div>
    );
}
