import { afterAll, expect, test } from "bun:test";

// The i18n / auth helpers touch browser storage during module initialization.
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
values.set("flora:auth_token", "test-token");
afterAll(() => {
    if (originalStorage) Object.defineProperty(globalThis, "localStorage", originalStorage);
    else Reflect.deleteProperty(globalThis, "localStorage");
});

const { FloraApiError } = await import("../src/services/api/flora-client");
const { adminRetryJob, adminTopUpWallet, adminUpdateModel, adminUpsertI18nMessage, fetchAdminI18nMessages, fetchAdminJobs, fetchAdminModels, fetchAdminUsers, fetchAdminWalletLedger, fetchEnabledModels } = await import("../src/services/api/flora-admin");

const USER_ID = "22222222-2222-2222-2222-222222222222";
const JOB_ID = "33333333-3333-3333-3333-333333333333";

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

const adminUser = { user_id: USER_ID, username: "demo", role: "user", locale: "zh", available: 120, frozen: 30, created_at: "2026-10-24T00:00:00Z", updated_at: "2026-10-24T01:00:00Z" };

test("the user list is read from the admin users endpoint", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: [adminUser], error_code: null, message: null }));
    const users = await fetchAdminUsers({ q: "dem", limit: 50 }, impl);
    expect(calls[0].url).toBe("/api/v1/admin/users?q=dem&limit=50");
    expect(calls[0].method).toBe("GET");
    expect(users).toEqual([adminUser]);
});

test("the user list without a keyword asks for everything", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: [], error_code: null, message: null }));
    await fetchAdminUsers(undefined, impl);
    expect(calls[0].url).toBe("/api/v1/admin/users");
});

test("a top up posts the amount to the wallet endpoint", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: { user_id: USER_ID, available: 220, frozen: 0, updated_at: "2026-10-24T00:00:00Z" }, error_code: null, message: null }));
    const wallet = await adminTopUpWallet(USER_ID, 100, impl);
    expect(calls[0].method).toBe("POST");
    expect(calls[0].url).toBe(`/api/v1/admin/wallets/${USER_ID}/topup`);
    expect(JSON.parse(calls[0].body)).toEqual({ amount: 100 });
    expect(wallet.available).toBe(220);
});

test("the ledger of one user is listed by the wallet endpoint", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: [{ id: "l1", user_id: USER_ID, type: "topup", amount: 100, hold_id: null, job_id: null, created_at: "2026-10-24T00:00:00Z" }], error_code: null, message: null }));
    const entries = await fetchAdminWalletLedger(USER_ID, 50, impl);
    expect(calls[0].url).toBe(`/api/v1/admin/wallets/${USER_ID}/ledger?limit=50`);
    expect(entries[0].type).toBe("topup");
});

test("the job list is filtered by status and user", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: [], error_code: null, message: null }));
    await fetchAdminJobs({ status: "dead", userId: USER_ID, limit: 20 }, impl);
    expect(calls[0].url).toBe(`/api/v1/admin/jobs?status=dead&user_id=${USER_ID}&limit=20`);
});

test("a dead job carries its steps and the upstream raw text", async () => {
    const { impl } = createMockFetch(() =>
        json({
            ok: true,
            data: [
                {
                    job_id: JOB_ID,
                    user_id: USER_ID,
                    username: "demo",
                    status: "dead",
                    capability: "image.generate",
                    model: "gpt-image-2",
                    prompt: "a cat",
                    attempt: 3,
                    max_attempts: 3,
                    hold_id: null,
                    hold_status: null,
                    error_code: "PROVIDER_UNAVAILABLE",
                    created_at: "2026-10-24T00:00:00Z",
                    updated_at: "2026-10-24T01:00:00Z",
                    steps: [{ step_id: "s1", attempt: 3, status: "failed", error_code: "PROVIDER_UNAVAILABLE", provider_task_id: null, started_at: null, ended_at: null }],
                    provider_tasks: [{ id: "p1", job_step_id: null, upstream_request_id: "req-1", raw_status: "500", raw_body: { error: "boom" }, created_at: "2026-10-24T00:30:00Z" }],
                },
            ],
            error_code: null,
            message: null,
        }),
    );
    const jobs = await fetchAdminJobs({ status: "dead" }, impl);
    expect(jobs[0].provider_tasks[0].raw_status).toBe("500");
    expect(jobs[0].provider_tasks[0].raw_body).toEqual({ error: "boom" });
    expect(jobs[0].steps[0].attempt).toBe(3);
});

test("retrying a job posts to the retry endpoint", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: { job_id: JOB_ID, status: "queued", max_attempts: 4 }, error_code: null, message: null }));
    const job = await adminRetryJob(JOB_ID, impl);
    expect(calls[0].method).toBe("POST");
    expect(calls[0].url).toBe(`/api/v1/admin/jobs/${JOB_ID}/retry`);
    expect(job.status).toBe("queued");
});

test("the i18n messages are searched by keyword", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: [{ id: "m1", key: "WALLET_INSUFFICIENT", locale: "zh", template: "余额不足。", updated_at: "2026-10-24T00:00:00Z" }], error_code: null, message: null }));
    const messages = await fetchAdminI18nMessages("WALLET", impl);
    expect(calls[0].url).toBe("/api/v1/admin/i18n-messages?q=WALLET");
    expect(messages[0].key).toBe("WALLET_INSUFFICIENT");
});

test("an i18n message is upserted with key, locale and template", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: { id: "m1", key: "WALLET_INSUFFICIENT", locale: "zh", template: "余额不足，请联系管理员充值。", updated_at: "2026-10-24T00:00:00Z" }, error_code: null, message: null }));
    await adminUpsertI18nMessage({ key: "WALLET_INSUFFICIENT", locale: "zh", template: "余额不足，请联系管理员充值。" }, impl);
    expect(calls[0].method).toBe("PUT");
    expect(calls[0].url).toBe("/api/v1/admin/i18n-messages");
    expect(JSON.parse(calls[0].body)).toEqual({ key: "WALLET_INSUFFICIENT", locale: "zh", template: "余额不足，请联系管理员充值。" });
});

test("the model display config is listed and updated", async () => {
    const { calls, impl } = createMockFetch((url) =>
        url.endsWith("/topup") || url.includes("/retry")
            ? json({ ok: true, data: {}, error_code: null, message: null })
            : json({ ok: true, data: [{ id: "c1", model: "gpt-image-2", capability: "image.generate", display_name: "GPT Image 2", enabled: true, sort_order: 10, updated_at: "2026-10-24T00:00:00Z" }], error_code: null, message: null }),
    );
    const models = await fetchAdminModels(impl);
    expect(calls[0].url).toBe("/api/v1/admin/models");
    expect(models[0].model).toBe("gpt-image-2");

    await adminUpdateModel("gpt-image-2", { display_name: "GPT Image 2", capability: "image.generate", enabled: false, sort_order: 10 }, impl);
    expect(calls[1].method).toBe("PUT");
    expect(calls[1].url).toBe("/api/v1/admin/models/gpt-image-2");
    expect(JSON.parse(calls[1].body)).toEqual({ display_name: "GPT Image 2", capability: "image.generate", enabled: false, sort_order: 10 });
});

test("the client model list only reads the public models endpoint", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: [{ id: "c1", model: "gpt-image-2", capability: "image.generate", display_name: "GPT Image 2", enabled: true, sort_order: 10, updated_at: "2026-10-24T00:00:00Z" }], error_code: null, message: null }));
    const models = await fetchEnabledModels(impl);
    expect(calls[0].url).toBe("/api/v1/models");
    expect(models[0].enabled).toBe(true);
});

test("a normal user gets the FORBIDDEN code instead of the data", async () => {
    const { impl } = createMockFetch(() => json({ ok: false, data: null, error_code: "FORBIDDEN", message: "无权访问管理后台。" }));
    let caught: unknown = null;
    try {
        await fetchAdminUsers(undefined, impl);
    } catch (error) {
        caught = error;
    }
    expect(caught).toBeInstanceOf(FloraApiError);
    expect((caught as FloraApiError).errorCode).toBe("FORBIDDEN");
    expect((caught as FloraApiError).message).toBe("无权访问管理后台。");
});
