import { cn } from "@/lib/utils";

const CORAL = "#E8867A";

/** Concept D mark: one continuous horizontal dual-loop ribbon (viewBox 0 0 56 40). */
const MARK_RIBBON_PATH =
    "M11 32.2V26.4C11 26.4 9.4 19.6 12.6 15.4C15.4 11.8 20.2 10.4 24.8 12.2C27 13.2 27.9 15.8 27.9 18.4C27.9 15.8 28.8 13.2 31 12.2C35.6 10.4 40.4 11.8 43.2 15.4C46.4 19.6 44.8 26.4 44.8 26.4V32.2";

type PilipalaLogoProps = {
    variant?: "mark" | "lockup";
    className?: string;
    title?: string;
};

function MarkPaths() {
    return (
        <>
            <path
                d={MARK_RIBBON_PATH}
                stroke="currentColor"
                strokeWidth="4.8"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
            <rect x="24.6" y="15.2" width="6.8" height="6.8" rx="1.6" transform="rotate(45 28 19.6)" fill={CORAL} />
        </>
    );
}

/** pilipala 概念 D：双 p 丝带标 + 可选字标 lockup。 */
export function PilipalaLogo({ variant = "mark", className, title = "pilipala" }: PilipalaLogoProps) {
    if (variant === "lockup") {
        return (
            <svg className={cn("block shrink-0 text-white", className)} viewBox="0 0 120 88" fill="none" role="img" aria-label={title}>
                <g transform="translate(32 4)">
                    <MarkPaths />
                </g>
                <text
                    x="60"
                    y="78"
                    textAnchor="middle"
                    fill="currentColor"
                    fontFamily="ui-rounded, 'SF Pro Rounded', 'Nunito', 'Varela Round', system-ui, sans-serif"
                    fontSize="22"
                    fontWeight="600"
                    letterSpacing="-0.02em"
                >
                    pilipala
                </text>
            </svg>
        );
    }

    return (
        <svg className={cn("block shrink-0 text-white", className)} viewBox="0 0 56 40" fill="none" role="img" aria-label={title}>
            <MarkPaths />
        </svg>
    );
}
