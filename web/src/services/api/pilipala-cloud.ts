import { pilipalaRequest } from "./pilipala-client";
import type { PilipalaJobStatus } from "./pilipala-jobs";
import type { CanvasBackgroundMode } from "@/lib/canvas-theme";
import type { ImageAsset, VideoAsset } from "@/stores/use-asset-store";
import type { CanvasProject } from "@/stores/canvas/use-canvas-store";
import type { CanvasAssistantSession, CanvasConnection, CanvasNodeData, ViewportTransform } from "@/types/canvas";

export type PilipalaProjectSummary = {
    id: string;
    name: string;
    version: number;
    created_at: string;
    updated_at: string;
};

export type PilipalaCanvasDataSnapshot = {
    nodes?: CanvasNodeData[];
    connections?: CanvasConnection[];
    chatSessions?: CanvasAssistantSession[];
    activeChatId?: string | null;
    backgroundMode?: CanvasBackgroundMode;
    showImageInfo?: boolean;
    viewport?: ViewportTransform;
};

export type PilipalaProjectDetail = PilipalaProjectSummary & {
    canvas_data: PilipalaCanvasDataSnapshot;
};

export type PilipalaUserAsset = {
    id: string;
    project_id: string | null;
    job_id: string | null;
    source: "generated" | "uploaded";
    object_key: string;
    signed_url: string | null;
    mime_type: string;
    created_at: string;
};

export type PilipalaJobHistoryItem = {
    job_id: string;
    status: PilipalaJobStatus;
    capability: string;
    model: string;
    prompt: string;
    cost_credits: number;
    result_urls: string[];
    error_code: string | null;
    message: string | null;
    created_at: string;
    updated_at: string;
    finished_at: string | null;
};

export type PilipalaCloudCallOptions = { fetchImpl?: typeof fetch };

/** 云端项目 id 是后端 UUID；本地项目用 nanoid，两者不能混用。 */
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isCloudProjectId(value: string) {
    return UUID_PATTERN.test(value.trim());
}

export function listCloudProjects(options?: PilipalaCloudCallOptions) {
    return pilipalaRequest<PilipalaProjectSummary[]>("/api/v1/projects", { fetchImpl: options?.fetchImpl });
}

export function getCloudProject(projectId: string, options?: PilipalaCloudCallOptions) {
    return pilipalaRequest<PilipalaProjectDetail>(`/api/v1/projects/${encodeURIComponent(projectId)}`, { fetchImpl: options?.fetchImpl });
}

export function createCloudProject(input: { name: string; canvas_data?: PilipalaCanvasDataSnapshot }, options?: PilipalaCloudCallOptions) {
    return pilipalaRequest<PilipalaProjectDetail>("/api/v1/projects", {
        method: "POST",
        body: JSON.stringify({ name: input.name, canvas_data: input.canvas_data ?? {} }),
        fetchImpl: options?.fetchImpl,
    });
}

export function updateCloudProject(projectId: string, input: { name?: string; canvas_data?: PilipalaCanvasDataSnapshot }, options?: PilipalaCloudCallOptions) {
    return pilipalaRequest<PilipalaProjectDetail>(`/api/v1/projects/${encodeURIComponent(projectId)}`, {
        method: "PUT",
        body: JSON.stringify({ ...(input.name ? { name: input.name } : {}), ...(input.canvas_data ? { canvas_data: input.canvas_data } : {}) }),
        fetchImpl: options?.fetchImpl,
    });
}

export function deleteCloudProject(projectId: string, options?: PilipalaCloudCallOptions) {
    return pilipalaRequest<{ id: string; deleted: boolean }>(`/api/v1/projects/${encodeURIComponent(projectId)}`, { method: "DELETE", fetchImpl: options?.fetchImpl });
}

export function listCloudAssets(options?: PilipalaCloudCallOptions & { limit?: number }) {
    const limit = options?.limit ?? 100;
    return pilipalaRequest<PilipalaUserAsset[]>(`/api/v1/assets?limit=${limit}`, { fetchImpl: options?.fetchImpl });
}

export function deleteCloudAsset(assetId: string, options?: PilipalaCloudCallOptions) {
    return pilipalaRequest<{ id: string; deleted: boolean }>(`/api/v1/assets/${encodeURIComponent(assetId)}`, { method: "DELETE", fetchImpl: options?.fetchImpl });
}

export function listPilipalaJobHistory(options?: PilipalaCloudCallOptions & { limit?: number }) {
    const limit = options?.limit ?? 50;
    return pilipalaRequest<PilipalaJobHistoryItem[]>(`/api/v1/jobs/history?limit=${limit}`, { fetchImpl: options?.fetchImpl });
}

/** 画布快照：只带可序列化的画布状态，图片字节仍留在浏览器本地（ADR 0003）。 */
export function toCanvasDataSnapshot(project: Pick<CanvasProject, "nodes" | "connections" | "chatSessions" | "activeChatId" | "backgroundMode" | "showImageInfo" | "viewport">): PilipalaCanvasDataSnapshot {
    return {
        nodes: project.nodes ?? [],
        connections: project.connections ?? [],
        chatSessions: project.chatSessions ?? [],
        activeChatId: project.activeChatId ?? null,
        backgroundMode: project.backgroundMode ?? "lines",
        showImageInfo: project.showImageInfo ?? false,
        viewport: project.viewport ?? { x: 0, y: 0, k: 1 },
    };
}

/** 云端详情 → 本地画布项目。 */
export function fromCloudProjectDetail(detail: PilipalaProjectDetail): CanvasProject {
    const data = detail.canvas_data ?? {};
    return {
        id: detail.id,
        title: detail.name,
        createdAt: detail.created_at,
        updatedAt: detail.updated_at,
        nodes: data.nodes ?? [],
        connections: data.connections ?? [],
        chatSessions: data.chatSessions ?? [],
        activeChatId: data.activeChatId ?? null,
        backgroundMode: data.backgroundMode ?? "lines",
        showImageInfo: data.showImageInfo ?? false,
        viewport: data.viewport ?? { x: 0, y: 0, k: 1 },
    };
}

/** 云端素材 → 本地素材。signed_url 由后端现签，失败时留空。 */
export function fromCloudAssetToStoreAsset(item: PilipalaUserAsset): ImageAsset | VideoAsset {
    const url = item.signed_url || "";
    // WP12：视频产物（.mp4 / video/*）映射成视频素材，不再当图片渲染。
    const isVideo = item.mime_type?.startsWith("video/") || item.object_key.toLowerCase().endsWith(".mp4");
    if (isVideo) {
        return {
            id: `cloud:${item.id}`,
            kind: "video",
            title: item.source === "generated" ? "cloud-generated" : "cloud-uploaded",
            coverUrl: "",
            tags: ["cloud", item.source],
            source: `cloud/${item.source}`,
            createdAt: item.created_at,
            updatedAt: item.created_at,
            data: { url, width: 1280, height: 720, bytes: 0, mimeType: item.mime_type || "video/mp4" },
        };
    }
    return {
        id: `cloud:${item.id}`,
        kind: "image",
        title: item.source === "generated" ? "cloud-generated" : "cloud-uploaded",
        coverUrl: url,
        tags: [],
        source: `cloud/${item.source}`,
        createdAt: item.created_at,
        updatedAt: item.created_at,
        data: { dataUrl: url, width: 0, height: 0, bytes: 0, mimeType: item.mime_type || "image/png" },
    };
}
