const LEGACY_PREFIX = "flora:";
const NEW_PREFIX = "pilipala:";

/** 将 localStorage 中 `flora:*` 键一次性迁到 `pilipala:*`（读到旧值则写入新键并删旧键）。 */
export function migrateLegacyLocalStorageKeys() {
    if (typeof globalThis.localStorage === "undefined") return;
    try {
        const keys: string[] = [];
        for (let i = 0; i < globalThis.localStorage.length; i++) {
            const key = globalThis.localStorage.key(i);
            if (key?.startsWith(LEGACY_PREFIX)) keys.push(key);
        }
        for (const legacyKey of keys) {
            const newKey = NEW_PREFIX + legacyKey.slice(LEGACY_PREFIX.length);
            if (globalThis.localStorage.getItem(newKey) != null) {
                globalThis.localStorage.removeItem(legacyKey);
                continue;
            }
            const value = globalThis.localStorage.getItem(legacyKey);
            if (value == null) continue;
            globalThis.localStorage.setItem(newKey, value);
            globalThis.localStorage.removeItem(legacyKey);
        }
    } catch {
        // 隐私模式等场景忽略
    }
}

/** 读 localStorage：新键优先，否则读旧键并迁到新键。 */
export function readMigratedLocalStorageItem(newKey: string, legacyKey?: string): string | null {
    migrateLegacyLocalStorageKeys();
    try {
        const direct = globalThis.localStorage?.getItem(newKey);
        if (direct != null) return direct;
        const legacy = legacyKey ? globalThis.localStorage?.getItem(legacyKey) : null;
        if (legacy != null) {
            globalThis.localStorage?.setItem(newKey, legacy);
            globalThis.localStorage?.removeItem(legacyKey!);
            return legacy;
        }
    } catch {
        return null;
    }
    return null;
}
