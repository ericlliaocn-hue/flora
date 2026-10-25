import { cn } from "@/lib/utils";

const CORAL = "#E8867A";

type PilipalaLogoProps = {
    variant?: "mark" | "lockup";
    className?: string;
    title?: string;
};

function MarkPaths() {
    return (
        <>
            <path
                d="M14.5 36V12.5C14.5 7.2 19.2 4 23.8 6.2C27.4 8 28.8 12.2 26.4 15.6C24.6 18.2 21.2 19.2 18.2 17.8"
                stroke="currentColor"
                strokeWidth="4.8"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
            <path
                d="M41.5 36V12.5C41.5 7.2 36.8 4 32.2 6.2C28.6 8 27.2 12.2 29.6 15.6C31.4 18.2 34.8 19.2 37.8 17.8"
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
