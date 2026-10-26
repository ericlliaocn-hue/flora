import { afterAll, expect, test } from "bun:test";

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

const { authIdentityPayload, authOtpDestination, normalizeEmailForApi, normalizePhoneForApi } = await import("../src/lib/auth-identity");
const { loginUser, registerUser, resetPassword, sendAuthOtp } = await import("../src/services/api/pilipala-auth");

type RecordedCall = { url: string; method: string; body: string };

function json(payload: unknown) {
    return new Response(JSON.stringify(payload), { status: 200, headers: { "Content-Type": "application/json" } });
}

function createMockFetch() {
    const calls: RecordedCall[] = [];
    const impl = (async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === "string" ? input : input.toString();
        calls.push({ url, method: init?.method || "GET", body: typeof init?.body === "string" ? init.body : "" });
        return json({ ok: true, data: { token: "t", user_id: "u1", phone: "+8617277889776", email: null, locale: "zh" }, error_code: null, message: null });
    }) as typeof fetch;
    return { calls, impl };
}

test("national CN mobiles and +86 / 86 prefixes normalize to the same E.164 phone", () => {
    expect(normalizePhoneForApi("17277889776")).toBe("+8617277889776");
    expect(normalizePhoneForApi("+8617277889776")).toBe("+8617277889776");
    expect(normalizePhoneForApi("86 172-7788-9776")).toBe("+8617277889776");
    expect(authIdentityPayload("phone", " 17277889776 ")).toEqual({ phone: "+8617277889776" });
    expect(authOtpDestination("phone", "17277889776")).toBe("+8617277889776");
});

test("email identity is trimmed and lowercased for send / login / register", () => {
    expect(normalizeEmailForApi("  User@Example.COM ")).toBe("user@example.com");
    expect(authIdentityPayload("email", "  User@Example.COM ")).toEqual({ email: "user@example.com" });
    expect(authOtpDestination("email", "User@Example.COM")).toBe("user@example.com");
});

test("phone OTP login posts phone + otp using backend LoginRequest field names", async () => {
    const { calls, impl } = createMockFetch();
    await loginUser({ phone: normalizePhoneForApi("17277889776"), otp: "132442" }, impl);
    expect(calls[0].url).toBe("/api/v1/auth/login");
    expect(JSON.parse(calls[0].body)).toEqual({ phone: "+8617277889776", otp: "132442" });
});

test("phone password login posts phone + password without an otp field", async () => {
    const { calls, impl } = createMockFetch();
    await loginUser({ phone: normalizePhoneForApi("17277889776"), password: "secret123" }, impl);
    expect(JSON.parse(calls[0].body)).toEqual({ phone: "+8617277889776", password: "secret123" });
});

test("phone registration posts phone + password + otp + locale", async () => {
    const { calls, impl } = createMockFetch();
    await registerUser({ phone: normalizePhoneForApi("17277889776"), password: "secret123", otp: "132442", locale: "zh" }, impl);
    expect(calls[0].url).toBe("/api/v1/auth/register");
    expect(JSON.parse(calls[0].body)).toEqual({
        phone: "+8617277889776",
        password: "secret123",
        otp: "132442",
        locale: "zh",
    });
});

test("OTP send uses the same E.164 destination as login", async () => {
    const { calls, impl } = createMockFetch();
    await sendAuthOtp({ channel: "sms", destination: authOtpDestination("phone", "17277889776"), purpose: "login" }, impl);
    expect(calls[0].url).toBe("/api/v1/auth/otp/send");
    expect(JSON.parse(calls[0].body)).toEqual({
        channel: "sms",
        destination: "+8617277889776",
        purpose: "login",
    });
});

test("password reset posts phone + otp + new_password", async () => {
    const { calls, impl } = createMockFetch();
    await resetPassword({ phone: normalizePhoneForApi("17277889776"), otp: "132442", new_password: "secret123" }, impl);
    expect(calls[0].url).toBe("/api/v1/auth/password/reset");
    expect(JSON.parse(calls[0].body)).toEqual({
        phone: "+8617277889776",
        otp: "132442",
        new_password: "secret123",
    });
});
