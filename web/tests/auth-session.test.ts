import { afterAll, beforeEach, expect, test } from "bun:test";

const values = new Map<string, string>();
const originalStorage = Object.getOwnPropertyDescriptor(globalThis, "localStorage");
Object.defineProperty(globalThis, "localStorage", {
    configurable: true,
    value: {
        getItem: (key: string) => values.get(key) ?? null,
        setItem: (key: string, value: string) => values.set(key, value),
        removeItem: (key: string) => values.delete(key),
    },
});
afterAll(() => {
    if (originalStorage) Object.defineProperty(globalThis, "localStorage", originalStorage);
    else Reflect.deleteProperty(globalThis, "localStorage");
});

const { PILIPALA_AUTH_TOKEN_KEY, PILIPALA_TOKEN_HEADER } = await import("../src/services/api/pilipala-client");
const { logoutUser } = await import("../src/services/api/pilipala-auth");
const { useAuthStore } = await import("../src/stores/use-auth-store");
const { shouldShowCreditsPill, walletAvailableDisplay } = await import("../src/components/layout/auth-session-menus");

function json(payload: unknown) {
    return new Response(JSON.stringify(payload), { status: 200, headers: { "Content-Type": "application/json" } });
}

beforeEach(() => {
    values.clear();
    useAuthStore.setState({ token: null, user: null, wallet: null, loading: false, initialized: true, authModalOpen: false, authModalTab: "login" });
});

test("logged-out chrome hides the fake credits pill", () => {
    expect(shouldShowCreditsPill(null)).toBe(false);
    expect(walletAvailableDisplay(null)).toBe("—");
    expect(walletAvailableDisplay({ user_id: "u1", available: 42, frozen: 3, updated_at: "2026-10-26T00:00:00Z" })).toBe("42");
});

test("logout sends the captured token once even if local state is already cleared", async () => {
    const headers: string[] = [];
    const impl = (async (_input: RequestInfo | URL, init?: RequestInit) => {
        const raw = init?.headers;
        const record = raw && !Array.isArray(raw) && !(raw instanceof Headers) ? (raw as Record<string, string>) : {};
        headers.push(record[PILIPALA_TOKEN_HEADER] ?? "");
        return json({ ok: true, data: {}, error_code: null, message: null });
    }) as typeof fetch;

    await logoutUser(impl, "session-token");
    expect(headers).toEqual(["session-token"]);
});

test("rapid logout clicks only hit /auth/logout once", async () => {
    values.set(PILIPALA_AUTH_TOKEN_KEY, "session-token");
    useAuthStore.setState({
        token: "session-token",
        user: { user_id: "u1", phone: "+8617277889776", email: null, locale: "zh" },
        wallet: { user_id: "u1", available: 10, frozen: 0, updated_at: "2026-10-26T00:00:00Z" },
    });

    const urls: string[] = [];
    const impl = (async (input: RequestInfo | URL) => {
        urls.push(typeof input === "string" ? input : input.toString());
        return json({ ok: true, data: {}, error_code: null, message: null });
    }) as typeof fetch;

    await Promise.all([useAuthStore.getState().logout(impl), useAuthStore.getState().logout(impl)]);
    expect(urls.filter((url) => url.includes("/api/v1/auth/logout"))).toHaveLength(1);
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
    expect(values.get(PILIPALA_AUTH_TOKEN_KEY)).toBeUndefined();
});
