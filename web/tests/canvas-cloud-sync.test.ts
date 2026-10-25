import { beforeEach, expect, test } from "bun:test";

// The auth token lives in localStorage; the canvas store hydrates from localforage.
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

const { useCanvasStore } = await import("../src/stores/canvas/use-canvas-store");

const CLOUD_ID = "11111111-1111-1111-1111-111111111111";
const LOCAL_ID = "V1StGXR8_Z5jdHi6B-myT";

type RecordedCall = { url: string; method: string; body: string };

function json(payload: unknown) {
    return new Response(JSON.stringify(payload), { status: 200, headers: { "Content-Type": "application/json" } });
}

function createMockFetch(handler: (url: string, method: string) => Response | Promise<Response>) {
    const calls: RecordedCall[] = [];
    const impl = (async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = typeof input === "string" ? input : input.toString();
        calls.push({ url, method: init?.method || "GET", body: typeof init?.body === "string" ? init.body : "" });
        return await handler(url, init?.method || "GET");
    }) as typeof fetch;
    return { calls, impl };
}

function localProject(id: string, title: string) {
    return { id, title, createdAt: "2026-10-24T00:00:00Z", updatedAt: "2026-10-24T00:00:00Z", nodes: [], connections: [], chatSessions: [], activeChatId: null, backgroundMode: "lines" as const, showImageInfo: false, viewport: { x: 0, y: 0, k: 1 } };
}

beforeEach(() => {
    useCanvasStore.setState({ projects: [], deletedProjects: [], cloudSyncStatus: "local", cloudVersions: {} });
});

test("saving a local project pushes it to the cloud and swaps the local id for the cloud uuid", async () => {
    useCanvasStore.setState({ projects: [localProject(LOCAL_ID, "本地画布")] });
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: { id: CLOUD_ID, name: "本地画布", version: 1, canvas_data: {}, created_at: "2026-10-24T00:00:00Z", updated_at: "2026-10-24T00:00:00Z" }, error_code: null, message: null }));

    const savedId = await useCanvasStore.getState().saveProjectToCloud(LOCAL_ID, impl);

    expect(savedId).toBe(CLOUD_ID);
    expect(calls[0].method).toBe("POST");
    expect(calls[0].url).toBe("/api/v1/projects");
    expect(JSON.parse(calls[0].body).name).toBe("本地画布");
    expect(useCanvasStore.getState().projects[0].id).toBe(CLOUD_ID);
    expect(useCanvasStore.getState().cloudVersions[CLOUD_ID]).toBe(1);
    expect(useCanvasStore.getState().cloudSyncStatus).toBe("synced");
});

test("saving a cloud project updates the name and the snapshot and bumps the version", async () => {
    useCanvasStore.setState({ projects: [localProject(CLOUD_ID, "改名后的画布")] });
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: { id: CLOUD_ID, name: "改名后的画布", version: 4, canvas_data: {}, created_at: "2026-10-24T00:00:00Z", updated_at: "2026-10-24T00:00:00Z" }, error_code: null, message: null }));

    const savedId = await useCanvasStore.getState().saveProjectToCloud(CLOUD_ID, impl);

    expect(savedId).toBe(CLOUD_ID);
    expect(calls[0].method).toBe("PUT");
    expect(calls[0].url).toBe(`/api/v1/projects/${CLOUD_ID}`);
    const body = JSON.parse(calls[0].body);
    expect(body.name).toBe("改名后的画布");
    expect(body.canvas_data.nodes).toEqual([]);
    expect(useCanvasStore.getState().cloudVersions[CLOUD_ID]).toBe(4);
});

test("a failing save marks the sync status as error instead of pretending it synced", async () => {
    useCanvasStore.setState({ projects: [localProject(CLOUD_ID, "画布")] });
    const { impl } = createMockFetch(() => json({ ok: false, data: null, error_code: "PROJECT_NOT_FOUND", message: "项目不存在。" }));

    expect(await useCanvasStore.getState().saveProjectToCloud(CLOUD_ID, impl)).toBeNull();
    expect(useCanvasStore.getState().cloudSyncStatus).toBe("error");
});

test("without a token the sync stays local and no request is sent", async () => {
    values.delete("pilipala:auth_token");
    useCanvasStore.setState({ projects: [localProject(CLOUD_ID, "画布")] });
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: [], error_code: null, message: null }));

    await useCanvasStore.getState().syncProjectsFromCloud(impl);
    expect(useCanvasStore.getState().cloudSyncStatus).toBe("local");

    expect(await useCanvasStore.getState().saveProjectToCloud(CLOUD_ID, impl)).toBeNull();
    expect(calls.length).toBe(0);
    values.set("pilipala:auth_token", "test-token");
});

test("the cloud project list merges into the local list and records the versions", async () => {
    useCanvasStore.setState({ projects: [localProject("local-only", "只在本地的画布")] });
    const { impl } = createMockFetch(() =>
        json({ ok: true, data: [{ id: CLOUD_ID, name: "云端画布", version: 7, created_at: "2026-10-24T00:00:00Z", updated_at: "2026-10-24T01:00:00Z" }], error_code: null, message: null }),
    );

    await useCanvasStore.getState().syncProjectsFromCloud(impl);

    const projects = useCanvasStore.getState().projects;
    expect(projects.map((project) => project.id)).toEqual([CLOUD_ID, "local-only"]);
    expect(projects[0].title).toBe("云端画布");
    expect(projects[0].nodes).toEqual([]);
    expect(useCanvasStore.getState().cloudVersions[CLOUD_ID]).toBe(7);
    expect(useCanvasStore.getState().cloudSyncStatus).toBe("synced");
});

test("deleting from the cloud only touches the cloud uuid projects", async () => {
    const { calls, impl } = createMockFetch(() => json({ ok: true, data: { id: CLOUD_ID, deleted: true }, error_code: null, message: null }));

    await useCanvasStore.getState().deleteProjectsFromCloud([LOCAL_ID, CLOUD_ID], impl);

    expect(calls.length).toBe(1);
    expect(calls[0].url).toBe(`/api/v1/projects/${CLOUD_ID}`);
});
