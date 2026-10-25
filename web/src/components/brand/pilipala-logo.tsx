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
                d="M12.4 35.6V14.2C12.4 6.4 20.6 2.8 27.4 6.6C32.6 9.8 33.6 15.8 30.2 20C28 22.8 24.2 23.6 20.8 21.8C17.2 20 12.4 17.2 12.4 14.2"
                stroke="currentColor"
                strokeWidth="4.8"
                strokeLinecap="round"
                strokeLinejoin="round"
            />
            <path
                d="M43.6 35.6V14.2C43.6 6.4 35.4 2.8 28.6 6.6C23.4 9.8 22.4 15.8 25.8 20C28 22.8 31.8 23.6 35.2 21.8C38.8 20 43.6 17.2 43.6 14.2"
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
