import { cn } from "@/lib/utils";

<<<<<<< HEAD
=======
const MARK_SRC = "/logo-mark.png";
const LOCKUP_SRC = "/logo-lockup.png";

>>>>>>> origin/main
type PilipalaLogoProps = {
    variant?: "mark" | "lockup";
    className?: string;
    title?: string;
};

<<<<<<< HEAD
/** pilipala 概念 D：透明底 PNG 标（mark 支持 3x srcSet）。 */
export function PilipalaLogo({ variant = "mark", className, title = "pilipala" }: PilipalaLogoProps) {
    if (variant === "lockup") {
        return (
            <img
                src="/logo-lockup.png"
                alt={title}
                className={cn("block shrink-0 object-contain", className)}
            />
        );
    }

    return (
        <img
            src="/logo-mark.png"
            srcSet="/logo-mark.png 1x, /logo-mark-3x.png 3x"
            alt={title}
            className={cn("block shrink-0 object-contain", className)}
        />
    );
=======
/** pilipala 概念 D：锁定稿 PNG 图形标 + lockup（无手绘 SVG 路径）。 */
export function PilipalaLogo({ variant = "mark", className, title = "pilipala" }: PilipalaLogoProps) {
    const src = variant === "lockup" ? LOCKUP_SRC : MARK_SRC;
    return <img src={src} alt={title} className={cn("block shrink-0 object-contain", className)} decoding="async" />;
>>>>>>> origin/main
}
