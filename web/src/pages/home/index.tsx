import { Bot, FileText, ImageIcon, LayoutGrid, Plus, Video } from "lucide-react";
import { useEffect, useState } from "react";
import { App, Image } from "antd";
import { Link, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";

import { DOCS_URL } from "@/constant/env";
import { fetchPrompts, type Prompt } from "@/services/api/prompts";
import i18n from "@/i18n";
import { cn } from "@/lib/utils";
import { useAgentStore } from "@/stores/use-agent-store";

const aspectClasses = ["aspect-[3/4]", "aspect-[4/3]", "aspect-square", "aspect-[2/3]", "aspect-[16/10]"] as const;

const featureGradients = [
    "from-[#1a2332] via-[#2d1f3d] to-[#1e2a28]",
    "from-[#1f2a1c] via-[#2a2418] to-[#1a2230]",
    "from-[#1c1f2e] via-[#243040] to-[#2a1f2e]",
    "from-[#2a2218] via-[#1e2830] to-[#241e2a]",
] as const;

export default function IndexPage() {
    const { message } = App.useApp();
    const { t } = useTranslation();
    const navigate = useNavigate();
    const toggleAgent = useAgentStore((state) => state.togglePanel);
    const [promptShowcase, setPromptShowcase] = useState<Prompt[]>([]);
    const [previewIndex, setPreviewIndex] = useState(0);
    const [previewOpen, setPreviewOpen] = useState(false);

    useEffect(() => {
        void fetchPrompts({ pageSize: 12 })
            .then((data) => setPromptShowcase(data.items))
            .catch((error) => message.error(error instanceof Error ? error.message : i18n.t("home.promptError")));
    }, [message]);

    const chips = [
        { key: "canvas", label: t("home.chips.canvas"), icon: LayoutGrid, href: "/canvas" },
        { key: "image", label: t("home.chips.image"), icon: ImageIcon, href: "/image" },
        { key: "video", label: t("home.chips.video"), icon: Video, href: "/video" },
        { key: "prompts", label: t("home.chips.prompts"), icon: FileText, href: "/prompts" },
        { key: "agent", label: t("home.chips.agent"), icon: Bot, onClick: toggleAgent },
    ] as const;

    const features = [
        { title: t("home.features.iteration.title"), body: t("home.features.iteration.body"), art: "f1" },
        { title: t("home.features.prepaid.title"), body: t("home.features.prepaid.body"), art: "f2" },
        { title: t("home.features.gateway.title"), body: t("home.features.gateway.body"), art: "f3" },
        { title: t("home.features.reuse.title"), body: t("home.features.reuse.body"), art: "f4" },
    ] as const;

    return (
        <main className="h-full overflow-y-auto bg-[#0b0b0b] px-3.5 pb-14 pt-2.5 text-neutral-100 md:px-3.5">
            <div className="mx-auto max-w-[1120px]">
                <section
                    className="relative flex min-h-[520px] flex-col items-center justify-center overflow-hidden rounded-[20px] border border-white/[0.08] bg-[#121212] px-10 py-12 md:min-h-[calc(100dvh-12rem)] md:max-h-[920px]"
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
                    <div className="pointer-events-none absolute inset-6 rounded-[14px] border border-white/[0.05]" aria-hidden="true" />
                    <p className="relative z-[1] mb-7 text-[13px] uppercase tracking-[0.16em] text-neutral-500">{t("home.hero.kicker")}</p>
                    <div className="relative z-[1]">
                        <button
                            type="button"
                            className="inline-flex h-[68px] items-center gap-3 rounded-full bg-[#e8e8e8] px-11 text-[19px] font-semibold tracking-tight text-[#0b0b0b] shadow-[0_0_0_1px_rgba(255,255,255,0.1),0_0_0_8px_rgba(255,255,255,0.03),0_16px_48px_rgba(0,0,0,0.5)] transition hover:bg-white active:scale-[0.98]"
                            onClick={() => navigate("/canvas")}
                        >
                            <Plus className="size-5" strokeWidth={1.8} />
                            {t("home.createCanvas")}
                        </button>
                    </div>
                    <p className="relative z-[1] mt-6 max-w-[500px] text-center text-base leading-relaxed text-neutral-400">{t("home.hero.subtitle")}</p>
                    <p className="relative z-[1] mt-8 text-[13.5px] tracking-wide text-neutral-500">
                        <strong className="font-medium text-neutral-400">flora</strong>
                        <span className="text-neutral-600"> · </span>
                        {t("home.hero.mark")}
                    </p>
                </section>

                <div className="mx-auto mb-10 mt-6 flex max-w-[1120px] flex-wrap justify-center gap-2.5" aria-label={t("home.chips.label")}>
                    {chips.map((chip) => {
                        const Icon = chip.icon;
                        const className =
                            "inline-flex h-[38px] items-center gap-2 rounded-full border border-white/[0.08] bg-[#121212] px-4 text-[13px] text-neutral-400 transition hover:border-white/[0.14] hover:bg-[#1a1a1a] hover:text-neutral-100";
                        if ("href" in chip && chip.href) {
                            return (
                                <Link key={chip.key} to={chip.href} className={className}>
                                    <Icon className="size-4" strokeWidth={1.6} />
                                    {chip.label}
                                </Link>
                            );
                        }
                        return (
                            <button key={chip.key} type="button" className={className} onClick={chip.onClick}>
                                <Icon className="size-4" strokeWidth={1.6} />
                                {chip.label}
                            </button>
                        );
                    })}
                </div>

                <section className="mb-11">
                    <div className="mb-4 flex items-baseline justify-between px-0.5">
                        <h2 className="text-[15px] font-semibold tracking-tight">{t("home.features.title")}</h2>
                        <span className="text-[12.5px] text-neutral-500">{t("home.features.hint")}</span>
                    </div>
                    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
                        {features.map((feature, index) => (
                            <article key={feature.art} className="flex min-h-[210px] flex-col overflow-hidden rounded-[14px] border border-white/[0.08] bg-[#161616] transition hover:-translate-y-px hover:border-white/[0.14]">
                                <div className={cn("relative h-[100px] bg-gradient-to-br", featureGradients[index])} aria-hidden="true">
                                    <FeatureArt variant={feature.art} />
                                    <div className="absolute inset-0 bg-gradient-to-b from-transparent via-transparent to-[#161616]" />
                                </div>
                                <div className="flex flex-1 flex-col gap-1.5 px-3.5 pb-4 pt-3">
                                    <h3 className="text-sm font-semibold">{feature.title}</h3>
                                    <p className="text-[12.5px] leading-relaxed text-neutral-400">{feature.body}</p>
                                </div>
                            </article>
                        ))}
                    </div>
                </section>

                <section className="mb-11" id="showcase" aria-labelledby="home-showcase-title">
                    <div className="mb-4 flex items-baseline justify-between px-0.5">
                        <h2 id="home-showcase-title" className="text-[15px] font-semibold tracking-tight">{t("home.showcaseTitle")}</h2>
                        <span className="text-[12.5px] text-neutral-500">{t("home.showcaseHint")}</span>
                    </div>
                    <div className="columns-2 gap-3 md:columns-3 xl:columns-4">
                        {promptShowcase.map((item, index) => (
                            <button
                                key={item.id}
                                type="button"
                                onClick={() => {
                                    setPreviewIndex(index);
                                    setPreviewOpen(true);
                                }}
                                className="mb-3 w-full break-inside-avoid overflow-hidden rounded-[14px] border border-white/[0.08] bg-[#161616] text-left transition hover:-translate-y-px hover:border-white/[0.14]"
                            >
                                <img
                                    src={item.coverUrl}
                                    alt={item.title}
                                    className={cn("w-full object-cover bg-[#1a1a1a]", aspectClasses[index % aspectClasses.length])}
                                    loading="lazy"
                                />
                                <div className="px-3 py-2.5">
                                    <h4 className="truncate text-[13px] font-medium">{item.title}</h4>
                                    <p className="mt-0.5 truncate text-[11.5px] text-neutral-500">{item.tags[0] ?? t("home.showcaseFallback")}</p>
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

function FeatureArt({ variant }: { variant: string }) {
    if (variant === "f1") {
        return (
            <>
                <span className="absolute left-[18%] top-[28%] size-12 rounded-lg border border-white/10 bg-white/[0.06]" />
                <span className="absolute left-[42%] top-[22%] h-10 w-14 rounded-lg border border-white/10 bg-white/[0.06]" />
                <span className="absolute left-[62%] top-[38%] h-8 w-10 rounded-lg border border-white/10 bg-white/[0.06]" />
            </>
        );
    }
    if (variant === "f2") {
        return (
            <>
                <div className="absolute left-[22%] top-[22%] flex gap-1.5">
                    <span className="size-2 rounded-full bg-white/35" />
                    <span className="size-2 rounded-full bg-[rgba(120,220,160,0.55)]" />
                    <span className="size-2 rounded-full bg-white/20" />
                </div>
                <div className="absolute left-[18%] right-[18%] top-[42%] h-2.5 overflow-hidden rounded-md bg-white/[0.08]">
                    <span className="block h-full w-[62%] rounded-md bg-gradient-to-r from-white/35 to-white/15" />
                </div>
            </>
        );
    }
    if (variant === "f3") {
        return (
            <div className="absolute inset-0 flex items-center justify-center gap-2.5">
                <span className="size-7 rounded-lg border border-white/15 bg-white/[0.08]" />
                <span className="size-[34px] rounded-lg border border-white/15 bg-white/[0.14]" />
                <span className="size-7 rounded-lg border border-white/15 bg-white/[0.08]" />
            </div>
        );
    }
    return (
        <div className="absolute left-1/2 top-1/2 h-[52px] w-[70px] -translate-x-1/2 -translate-y-[55%]">
            <span className="absolute inset-0 translate-y-2.5 scale-90 rounded-lg border border-white/15 bg-white/[0.07] opacity-50" />
            <span className="absolute inset-0 translate-y-1 scale-95 rounded-lg border border-white/15 bg-white/[0.07] opacity-75" />
            <span className="absolute inset-0 -translate-y-1 rounded-lg border border-white/15 bg-white/[0.07]" />
        </div>
    );
}
