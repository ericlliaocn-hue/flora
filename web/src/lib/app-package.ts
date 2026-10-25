export const APP_PACKAGE_ID = "pilipala";

const LEGACY_APP_PACKAGE_IDS = new Set(["flora", APP_PACKAGE_ID]);

export function isKnownAppPackage(app: string | undefined | null) {
    return app != null && LEGACY_APP_PACKAGE_IDS.has(app);
}
