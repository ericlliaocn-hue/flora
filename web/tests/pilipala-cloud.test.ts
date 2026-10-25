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
values.set("pilipala:auth_token", "test-token");
afterAll(() => {
    if (originalStorage) Object.defineProperty(globalThis, "localStorage", originalStorage);
    else Reflect.deleteProperty(globalThis, "localStorage");
});

const { PilipalaApiError } = await import("../src/services/api/pilipala-client");
const { createCloudProject, deleteCloudAsset, deleteCloudProject, fromCloudAssetToStoreAsset, fromCloudProjectDetail, getCloudProject, isCloudProjectId, listCloudAssets, listCloudProjects, listPilipalaJobHistory, toCanvasDataSnapshot, updateCloudProject } = await import("../src/services/api/pilipala-cloud");

const PROJECT_ID = "11111111-1111-1111-1111-111111111111";

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

const summary = { id: PROJECT_ID, name: "云端画布", version: 3, created_at: "2026-10-24T00:00:00Z", updated_at: "2026-10-24T01:00:00Z" };

test("the project list is read from the account cloud endpoint", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: [summary], error_code: null, message: null }));
    const projects = await listCloudProjects({ fetchImpl: impl });
    expect(calls[0].url).toBe("/api/v1/projects");
    expect(calls[0].method).toBe("GET");
    expect(projects).toEqual([summary]);
});

test("a single project detail is fetched by its encoded id", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: { ...summary, canvas_data: { nodes: [] } }, error_code: null, message: null }));
    const detail = await getCloudProject(PROJECT_ID, { fetchImpl: impl });
    expect(calls[0].url).toBe(`/api/v1/projects/${PROJECT_ID}`);
    expect(detail.version).toBe(3);
});

test("creating a project posts the name and the canvas snapshot", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: { ...summary, version: 1, canvas_data: {} }, error_code: null, message: null }));
    await createCloudProject({ name: "新画布", canvas_data: { nodes: [], connections: [] } }, { fetchImpl: impl });
    expect(calls[0].method).toBe("POST");
    expect(calls[0].url).toBe("/api/v1/projects");
    expect(JSON.parse(calls[0].body)).toEqual({ name: "新画布", canvas_data: { nodes: [], connections: [] } });
});

test("updating a project puts the name and the snapshot", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: { ...summary, version: 4 }, error_code: null, message: null }));
    await updateCloudProject(PROJECT_ID, { name: "改名", canvas_data: { nodes: [] } }, { fetchImpl: impl });
    expect(calls[0].method).toBe("PUT");
    expect(calls[0].url).toBe(`/api/v1/projects/${PROJECT_ID}`);
    expect(JSON.parse(calls[0].body)).toEqual({ name: "改名", canvas_data: { nodes: [] } });
});

test("deleting a project and an asset both report the deleted id", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: { id: PROJECT_ID, deleted: true }, error_code: null, message: null }));
    expect(await deleteCloudProject(PROJECT_ID, { fetchImpl: impl })).toEqual({ id: PROJECT_ID, deleted: true });
    expect(await deleteCloudAsset("asset-1", { fetchImpl: impl })).toEqual({ id: PROJECT_ID, deleted: true });
    expect(calls[0].method).toBe("DELETE");
    expect(calls[1].url).toBe("/api/v1/assets/asset-1");
});

test("cloud assets and job history carry their limit in the query", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: [], error_code: null, message: null }));
    await listCloudAssets({ limit: 30, fetchImpl: impl });
    await listPilipalaJobHistory({ limit: 50, fetchImpl: impl });
    expect(calls[0].url).toBe("/api/v1/assets?limit=30");
    expect(calls[1].url).toBe("/api/v1/jobs/history?limit=50");
});

test("a business failure carries the stable error code instead of the raw payload", async () => {
    const { impl } = createMockFetch(() => json({ ok: false, data: null, error_code: "PROJECT_NOT_FOUND", message: "项目不存在" }));
    const failure = await getCloudProject(PROJECT_ID, { fetchImpl: impl }).catch((error: unknown) => error);
    expect(failure).toBeInstanceOf(PilipalaApiError);
    expect((failure as PilipalaApiError).errorCode).toBe("PROJECT_NOT_FOUND");
    expect((failure as PilipalaApiError).message).toBe("项目不存在");
});

test("the cloud token is sent as the pilipala auth header", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: [], error_code: null, message: null }));
    await listCloudProjects({ fetchImpl: impl });
    expect(calls[0].headers["pilipala-token"]).toBe("test-token");
});

test("only a backend uuid is treated as a cloud project id", () => {
    expect(isCloudProjectId(PROJECT_ID)).toBe(true);
    expect(isCloudProjectId("V1StGXR8_Z5jdHi6B-myT")).toBe(false);
});

test("the canvas snapshot keeps only the serializable canvas state", () => {
    const snapshot = toCanvasDataSnapshot({ nodes: [], connections: [], chatSessions: [], activeChatId: null, backgroundMode: "dots", showImageInfo: true, viewport: { x: 1, y: 2, k: 3 } });
    expect(snapshot).toEqual({ nodes: [], connections: [], chatSessions: [], activeChatId: null, backgroundMode: "dots", showImageInfo: true, viewport: { x: 1, y: 2, k: 3 } });
});

test("a cloud detail restores the local canvas project", () => {
    const project = fromCloudProjectDetail({ ...summary, canvas_data: { nodes: [], backgroundMode: "blank", viewport: { x: 5, y: 6, k: 2 } } });
    expect(project.id).toBe(PROJECT_ID);
    expect(project.title).toBe("云端画布");
    expect(project.backgroundMode).toBe("blank");
    expect(project.viewport).toEqual({ x: 5, y: 6, k: 2 });
    expect(project.connections).toEqual([]);
});

test("a cloud asset becomes an image asset addressed by its signed url", () => {
    const asset = fromCloudAssetToStoreAsset({ id: "asset-1", project_id: null, job_id: "job-1", source: "generated", object_key: "flora/img/1.png", signed_url: "https://flora-media-test.tos-cn-guangzhou.volces.com/flora/img/1.png?X-Tos-Signature=abc", mime_type: "image/png", created_at: "2026-10-24T00:00:00Z" });
    expect(asset.id).toBe("cloud:asset-1");
    expect(asset.kind).toBe("image");
    expect(asset.source).toBe("cloud/generated");
    expect(asset.data.dataUrl).toBe("https://flora-media-test.tos-cn-guangzhou.volces.com/flora/img/1.png?X-Tos-Signature=abc");
});
