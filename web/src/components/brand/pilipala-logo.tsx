import { cn } from "@/lib/utils";

const MARK_SRC = "/logo-mark.png";
const LOCKUP_SRC = "/logo-lockup.png";

type PilipalaLogoProps = {
    variant?: "mark" | "lockup";
    className?: string;
    title?: string;
};

/** pilipala 概念 D：锁定稿 PNG 图形标 + lockup（无手绘 SVG 路径）。 */
export function PilipalaLogo({ variant = "mark", className, title = "pilipala" }: PilipalaLogoProps) {
    const src = variant === "lockup" ? LOCKUP_SRC : MARK_SRC;
    return <img src={src} alt={title} className={cn("block shrink-0 object-contain", className)} decoding="async" />;
}
