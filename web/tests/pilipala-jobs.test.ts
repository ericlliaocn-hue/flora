import { afterAll, beforeEach, expect, test } from "bun:test";

// The config / i18n / auth stores touch browser storage during module initialization.
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
values.set("pilipala:auth_token", "test-token");
afterAll(() => {
    if (originalStorage) Object.defineProperty(globalThis, "localStorage", originalStorage);
    else Reflect.deleteProperty(globalThis, "localStorage");
});

const { defaultConfig } = await import("../src/stores/use-config-store");
const { useAuthStore } = await import("../src/stores/use-auth-store");
const { runPilipalaImageJob } = await import("../src/services/api/pilipala-jobs");
const { PilipalaApiError } = await import("../src/services/api/pilipala-client");

const JOB_ID = "11111111-1111-1111-1111-111111111111";
const SIGNED_URL = "http://127.0.0.1:18080/flora-dev/anytoken/img/abc/1.png?X-Tos-Signature=abc";
const PNG_BYTES = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

const config = { ...defaultConfig, channelMode: "remote" as const, model: "gpt-image-1", imageModel: "gpt-image-1" };

type RecordedCall = { url: string; method: string; headers: Record<string, string>; body: string };

function json(payload: unknown) {
    return new Response(JSON.stringify(payload), { status: 200, headers: { "Content-Type": "application/json" } });
}

function createMockFetch(handler: (url: string, method: string) => Response | Promise<Response>) {
    const calls: RecordedCall[] = [];
    const impl = (async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === "string" ? input : input.toString();
        const headers = (init?.headers || {}) as Record<string, string>;
        calls.push({ url, method: init?.method || "GET", headers, body: typeof init?.body === "string" ? init.body : "" });
        return await handler(url, init?.method || "GET");
    }) as typeof fetch;
    return { calls, impl };
}

function jobView(status: string, attempt: number, errorCode: string | null = null, message: string | null = null) {
    return { ok: true, data: { job_id: JOB_ID, status, attempt, max_attempts: 3, error_code: errorCode, message, updated_at: "2026-10-24T00:00:00Z" }, error_code: null, message: null };
}

beforeEach(() => {
    useAuthStore.setState({ token: "test-token", user: null, wallet: null, authModalOpen: false, authModalTab: "login" });
});

test("a managed image job submits, polls and returns the signed image", async () => {
    let polls = 0;
    const { calls, impl } = createMockFetch((url) => {
        if (url === "/api/v1/jobs/submit") return json({ ok: true, data: { job_id: JOB_ID, status: "queued", hold_id: "hold-1" }, error_code: null, message: null });
        if (url === `/api/v1/jobs/${JOB_ID}`) {
            polls += 1;
            return json(jobView(polls === 1 ? "queued" : polls === 2 ? "running" : "succeeded", polls));
        }
        if (url === `/api/v1/jobs/${JOB_ID}/result`) {
            return json({ ok: true, data: { job_id: JOB_ID, status: "succeeded", outputs: [{ type: "image", object_key: "anytoken/img/abc/1.png", url: SIGNED_URL }], error_code: null, message: null }, error_code: null, message: null });
        }
        if (url === "/api/v1/wallet") {
            return json({ ok: true, data: { user_id: "u-1", available: 90, frozen: 10, updated_at: "2026-10-24T00:00:00Z" }, error_code: null, message: null });
        }
        if (url.startsWith("http://127.0.0.1:18080")) return new Response(PNG_BYTES, { status: 200, headers: { "Content-Type": "image/png" } });
        return json({ ok: false, data: null, error_code: "VALIDATION_ERROR", message: `unexpected ${url}` });
    });

    const images = await runPilipalaImageJob(config, "a quiet garden", undefined, { fetchImpl: impl, pollIntervalMs: 10 });

    expect(images).toHaveLength(1);
    expect(images[0].dataUrl.startsWith("data:image/png;base64,")).toBe(true);
    expect(images[0].mimeType).toBe("image/png");
    expect(images[0].bytes).toBe(PNG_BYTES.length);
    expect(images[0].id.length).toBeGreaterThan(0);

    const submit = calls.find((call) => call.url === "/api/v1/jobs/submit");
    expect(submit?.method).toBe("POST");
    expect(submit?.headers["flora-token"]).toBe("test-token");
    expect(submit?.headers["Accept-Language"]).toBeTruthy();
    expect(submit?.headers.Authorization).toBeUndefined();

    const payload = JSON.parse(submit?.body || "{}");
    expect(payload.capability).toBe("image.generate");
    expect(payload.model).toBe("gpt-image-1");
    expect(payload.idempotency_key.length).toBeGreaterThan(0);
    expect(payload.params.prompt).toBe("a quiet garden");
    expect(payload.params.apiKey).toBeUndefined();
    expect(payload.params.api_key).toBeUndefined();
    expect(payload.params.baseUrl).toBeUndefined();

    // 轮询过、结果取过、钱包余额刷新过。
    expect(calls.filter((call) => call.url === `/api/v1/jobs/${JOB_ID}`).length).toBe(3);
    expect(calls.some((call) => call.url === `/api/v1/jobs/${JOB_ID}/result`)).toBe(true);
    expect(calls.some((call) => call.url === "/api/v1/wallet")).toBe(true);
});

test("an unauthorized submit opens the login dialog and keeps the backend message", async () => {
    const { impl } = createMockFetch((url) => {
        if (url === "/api/v1/jobs/submit") return json({ ok: false, data: null, error_code: "UNAUTHORIZED", message: "请先登录后再试。" });
        return json({ ok: false, data: null, error_code: "VALIDATION_ERROR", message: `unexpected ${url}` });
    });

    const error = await runPilipalaImageJob(config, "hi", undefined, { fetchImpl: impl, pollIntervalMs: 10 }).catch((reason) => reason);

    expect(error).toBeInstanceOf(PilipalaApiError);
    expect((error as PilipalaApiError).errorCode).toBe("UNAUTHORIZED");
    expect((error as PilipalaApiError).message).toBe("请先登录后再试。");
    expect(useAuthStore.getState().authModalOpen).toBe(true);
    expect(useAuthStore.getState().authModalTab).toBe("login");
});

test("an insufficient wallet keeps the backend message", async () => {
    const { impl } = createMockFetch((url) => {
        if (url === "/api/v1/jobs/submit") return json({ ok: false, data: null, error_code: "WALLET_INSUFFICIENT", message: "账户余额不足，请先充值。" });
        if (url === "/api/v1/wallet") return json({ ok: true, data: { user_id: "u-1", available: 0, frozen: 0, updated_at: "2026-10-24T00:00:00Z" }, error_code: null, message: null });
        return json({ ok: false, data: null, error_code: "VALIDATION_ERROR", message: `unexpected ${url}` });
    });

    const error = await runPilipalaImageJob(config, "hi", undefined, { fetchImpl: impl, pollIntervalMs: 10 }).catch((reason) => reason);

    expect(error).toBeInstanceOf(PilipalaApiError);
    expect((error as PilipalaApiError).errorCode).toBe("WALLET_INSUFFICIENT");
    expect((error as PilipalaApiError).message).toBe("账户余额不足，请先充值。");
    expect(useAuthStore.getState().authModalOpen).toBe(false);
});

test("a dead job surfaces the friendly backend error", async () => {
    const { impl } = createMockFetch((url) => {
        if (url === "/api/v1/jobs/submit") return json({ ok: true, data: { job_id: JOB_ID, status: "queued", hold_id: "hold-1" }, error_code: null, message: null });
        if (url === `/api/v1/jobs/${JOB_ID}`) return json(jobView("dead", 3, "PROVIDER_UNAVAILABLE", "生成服务暂时不可用，请稍后重试。"));
        if (url === "/api/v1/wallet") return json({ ok: true, data: { user_id: "u-1", available: 90, frozen: 0, updated_at: "2026-10-24T00:00:00Z" }, error_code: null, message: null });
        return json({ ok: false, data: null, error_code: "VALIDATION_ERROR", message: `unexpected ${url}` });
    });

    const error = await runPilipalaImageJob(config, "hi", undefined, { fetchImpl: impl, pollIntervalMs: 10 }).catch((reason) => reason);

    expect(error).toBeInstanceOf(PilipalaApiError);
    expect((error as PilipalaApiError).errorCode).toBe("PROVIDER_UNAVAILABLE");
    expect((error as PilipalaApiError).message).toBe("生成服务暂时不可用，请稍后重试。");
});

test("aborting during polling cancels the backend job", async () => {
    const controller = new AbortController();
    const { calls, impl } = createMockFetch((url) => {
        if (url === "/api/v1/jobs/submit") return json({ ok: true, data: { job_id: JOB_ID, status: "queued", hold_id: "hold-1" }, error_code: null, message: null });
        if (url === `/api/v1/jobs/${JOB_ID}`) {
            controller.abort();
            return json(jobView("running", 1));
        }
        if (url === `/api/v1/jobs/${JOB_ID}/cancel`) return json(jobView("cancelled", 1, "JOB_CANCELLED", "任务已取消。"));
        if (url === "/api/v1/wallet") return json({ ok: true, data: { user_id: "u-1", available: 90, frozen: 0, updated_at: "2026-10-24T00:00:00Z" }, error_code: null, message: null });
        return json({ ok: false, data: null, error_code: "VALIDATION_ERROR", message: `unexpected ${url}` });
    });

    const error = await runPilipalaImageJob(config, "hi", undefined, { fetchImpl: impl, pollIntervalMs: 10, signal: controller.signal }).catch((reason) => reason);

    expect((error as Error).message).toBe("请求已取消");
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(calls.some((call) => call.url === `/api/v1/jobs/${JOB_ID}/cancel` && call.method === "POST")).toBe(true);
});
