import { cn } from "@/lib/utils";

type PilipalaLogoProps = {
    variant?: "mark" | "lockup";
    className?: string;
    title?: string;
};

/** pilipala 概念 D：透明底 PNG 标（mark 支持 3x srcSet）。 */
export function PilipalaLogo({ variant = "mark", className, title = "pilipala" }: PilipalaLogoProps) {
    if (variant === "lockup") {
        return (
            <img
                src="/logo-lockup.png"
                alt={title}
                className={cn("block shrink-0 object-contain", className)}
                decoding="async"
            />
        );
    }

    return (
        <img
            src="/logo-mark.png"
            srcSet="/logo-mark.png 1x, /logo-mark-3x.png 3x"
            alt={title}
            className={cn("block shrink-0 object-contain", className)}
            decoding="async"
        />
    );
}
