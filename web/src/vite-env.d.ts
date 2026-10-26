/// <reference types="vite/client" />

declare const __APP_VERSION__: string;
declare const __APP_RELEASES__: import("@/lib/release").ReleaseInfo[];

interface ImportMetaEnv {
    // Comma-separated local development plugin URLs, refetched on every startup without caching or persistence.
    readonly VITE_DEV_PLUGINS?: string;
    // Optional build-time analytics configuration, with one independent variable per provider.
    // GA4 measurement ID (G-XXXX)
    readonly VITE_ANALYTICS_GA4_ID?: string;
    // Baidu Analytics site ID
    readonly VITE_ANALYTICS_BAIDU_ID?: string;
    /** ISO 8601 deadline used only when GET /api/v1/studio/features is unavailable */
    readonly VITE_STUDIO_PROMO_END_AT?: string;
    /** Optional local override when the features API is unavailable. Default is on. Set `false` to hide. */
    readonly VITE_STUDIO_PROMO_STRIP?: string;
}
