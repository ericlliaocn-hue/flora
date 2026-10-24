import { create } from "zustand";
import { persist, type PersistStorage, type StorageValue } from "zustand/middleware";

import { nanoid } from "nanoid";
import i18n from "@/i18n";
import { localForageStorage } from "@/lib/localforage-storage";
import type { CanvasBackgroundMode } from "@/lib/canvas-theme";
import { getStoredFloraToken } from "@/services/api/flora-client";
import { createCloudProject, deleteCloudProject, getCloudProject, isCloudProjectId, listCloudProjects, toCanvasDataSnapshot, updateCloudProject } from "@/services/api/flora-cloud";
import type { CanvasAssistantSession, CanvasConnection, CanvasNodeData, ViewportTransform } from "@/types/canvas";

export type CanvasProject = {
    id: string;
    title: string;
    createdAt: string;
    updatedAt: string;
    nodes: CanvasNodeData[];
    connections: CanvasConnection[];
    chatSessions: CanvasAssistantSession[];
    activeChatId: string | null;
    backgroundMode: CanvasBackgroundMode;
    showImageInfo: boolean;
    viewport: ViewportTransform;
};

export type CanvasDeletedProject = {
    id: string;
    deletedAt: string;
};

/** 云同步状态：local = 未登录只存本地，syncing / synced / error 对应云端交互。 */
export type CloudSyncStatus = "local" | "syncing" | "synced" | "error";

type CanvasStore = {
    hydrated: boolean;
    projects: CanvasProject[];
    deletedProjects: CanvasDeletedProject[];
    cloudSyncStatus: CloudSyncStatus;
    cloudVersions: Record<string, number>;
    createProject: (title?: string) => string;
    importProject: (project: Partial<CanvasProject>) => string;
    openProject: (id: string) => CanvasProject | null;
    renameProject: (id: string, title: string) => void;
    deleteProjects: (ids: string[]) => void;
    replaceProjects: (projects: CanvasProject[], deletedProjects?: CanvasDeletedProject[]) => void;
    updateProject: (id: string, patch: Partial<Pick<CanvasProject, "nodes" | "connections" | "chatSessions" | "activeChatId" | "backgroundMode" | "showImageInfo" | "viewport">>) => void;
    syncProjectsFromCloud: (fetchImpl?: typeof fetch) => Promise<void>;
    createCloudProjectAndOpen: (title?: string, fetchImpl?: typeof fetch) => Promise<string>;
    loadCloudProject: (projectId: string, fetchImpl?: typeof fetch) => Promise<boolean>;
    saveProjectToCloud: (projectId: string, fetchImpl?: typeof fetch) => Promise<string | null>;
    deleteProjectsFromCloud: (ids: string[], fetchImpl?: typeof fetch) => Promise<void>;
};

const initialViewport: ViewportTransform = { x: 0, y: 0, k: 1 };
const CANVAS_STORE_KEY = "flora:canvas_store";

/** 空画布骨架：本地新建、云端占位、云端详情还原都用它。 */
function emptyProject(id: string, title: string, createdAt?: string, updatedAt?: string): CanvasProject {
    const now = new Date().toISOString();
    return {
        id,
        title,
        createdAt: createdAt || now,
        updatedAt: updatedAt || now,
        ...EMPTY_CANVAS_DATA,
    };
}

const EMPTY_CANVAS_DATA: Pick<CanvasProject, "nodes" | "connections" | "chatSessions" | "activeChatId" | "backgroundMode" | "showImageInfo" | "viewport"> = {
    nodes: [],
    connections: [],
    chatSessions: [],
    activeChatId: null,
    backgroundMode: "lines",
    showImageInfo: false,
    viewport: initialViewport,
};
type PersistedCanvasState = Pick<CanvasStore, "projects" | "deletedProjects">;
let saveTimer: ReturnType<typeof setTimeout> | null = null;
let queuedPersistState: PersistedCanvasState | null = null;

const canvasStorage: PersistStorage<CanvasStore> = {
    getItem: async (name) => {
        const value = await localForageStorage.getItem(name);
        if (!value) return null;
        const parsed = JSON.parse(value) as StorageValue<CanvasStore>;
        queuedPersistState = parsed.state as PersistedCanvasState;
        return parsed;
    },
    setItem: (name, value) => {
        const nextState = value.state as PersistedCanvasState;
        if (queuedPersistState && queuedPersistState.projects === nextState.projects && queuedPersistState.deletedProjects === nextState.deletedProjects) return;
        queuedPersistState = nextState;
        if (saveTimer) clearTimeout(saveTimer);
        saveTimer = setTimeout(() => {
            saveTimer = null;
            void localForageStorage.setItem(name, JSON.stringify(value));
        }, 400);
    },
    removeItem: (name) => localForageStorage.removeItem(name),
};

export const useCanvasStore = create<CanvasStore>()(
    persist(
        (set, get) => ({
            hydrated: false,
            projects: [],
            deletedProjects: [],
            cloudSyncStatus: "local",
            cloudVersions: {},
            createProject: (title = i18n.t("canvas.project.untitled")) => {
                const id = nanoid();
                set((state) => ({ projects: [emptyProject(id, title), ...state.projects] }));
                return id;
            },
            importProject: (source) => {
                const now = new Date().toISOString();
                const project: CanvasProject = {
                    id: nanoid(),
                    title: source.title || i18n.t("canvas.project.imported"),
                    createdAt: source.createdAt || now,
                    updatedAt: now,
                    nodes: source.nodes || [],
                    connections: source.connections || [],
                    chatSessions: source.chatSessions || [],
                    activeChatId: source.activeChatId || null,
                    backgroundMode: source.backgroundMode || "lines",
                    showImageInfo: source.showImageInfo || false,
                    viewport: source.viewport || initialViewport,
                };
                set((state) => ({ projects: [project, ...state.projects] }));
                return project.id;
            },
            openProject: (id) => {
                return get().projects.find((item) => item.id === id) || null;
            },
            renameProject: (id, title) =>
                set((state) => ({
                    projects: state.projects.map((project) => (project.id === id ? { ...project, title: title.trim() || project.title, updatedAt: new Date().toISOString() } : project)),
                })),
            deleteProjects: (ids) =>
                set((state) => {
                    const now = new Date().toISOString();
                    const removing = new Set(ids);
                    const projects = state.projects.filter((project) => !removing.has(project.id));
                    const deletedProjects = [...state.deletedProjects.filter((item) => !removing.has(item.id)), ...ids.map((id) => ({ id, deletedAt: now }))];
                    return { projects, deletedProjects };
                }),
            replaceProjects: (projects, deletedProjects = []) => set({ projects, deletedProjects }),
            updateProject: (id, patch) =>
                set((state) => ({
                    projects: state.projects.map((project) => (project.id === id ? { ...project, ...patch, updatedAt: new Date().toISOString() } : project)),
                })),
            syncProjectsFromCloud: async (fetchImpl) => {
                if (!getStoredFloraToken()) {
                    set({ cloudSyncStatus: "local" });
                    return;
                }
                set({ cloudSyncStatus: "syncing" });
                try {
                    const summaries = await listCloudProjects({ fetchImpl });
                    set((state) => {
                        const known = new Set(state.projects.map((project) => project.id));
                        const fresh = summaries.filter((summary) => !known.has(summary.id)).map((summary) => emptyProject(summary.id, summary.name, summary.created_at, summary.updated_at));
                        const versions = { ...state.cloudVersions };
                        summaries.forEach((summary) => {
                            versions[summary.id] = summary.version;
                        });
                        const merged = state.projects.map((project) => {
                            const summary = summaries.find((item) => item.id === project.id);
                            return summary ? { ...project, title: summary.name, updatedAt: summary.updated_at } : project;
                        });
                        return { projects: [...fresh, ...merged], cloudVersions: versions, cloudSyncStatus: "synced" };
                    });
                } catch {
                    set({ cloudSyncStatus: "error" });
                }
            },
            createCloudProjectAndOpen: async (title, fetchImpl) => {
                const name = title || i18n.t("canvas.project.untitled");
                if (!getStoredFloraToken()) {
                    set({ cloudSyncStatus: "local" });
                    return get().createProject(name);
                }
                set({ cloudSyncStatus: "syncing" });
                try {
                    const detail = await createCloudProject({ name, canvas_data: toCanvasDataSnapshot(EMPTY_CANVAS_DATA) }, { fetchImpl });
                    const project = emptyProject(detail.id, detail.name, detail.created_at, detail.updated_at);
                    set((state) => ({
                        projects: [project, ...state.projects],
                        cloudVersions: { ...state.cloudVersions, [detail.id]: detail.version },
                        cloudSyncStatus: "synced",
                    }));
                    return detail.id;
                } catch {
                    // 云端建不了就退回本地项目，状态标记为 error 让界面如实提示。
                    set({ cloudSyncStatus: "error" });
                    return get().createProject(name);
                }
            },
            loadCloudProject: async (projectId, fetchImpl) => {
                if (!getStoredFloraToken() || !isCloudProjectId(projectId)) return false;
                set({ cloudSyncStatus: "syncing" });
                try {
                    const detail = await getCloudProject(projectId, { fetchImpl });
                    const project = emptyProject(detail.id, detail.name, detail.created_at, detail.updated_at);
                    project.nodes = detail.canvas_data?.nodes ?? [];
                    project.connections = detail.canvas_data?.connections ?? [];
                    project.chatSessions = detail.canvas_data?.chatSessions ?? [];
                    project.activeChatId = detail.canvas_data?.activeChatId ?? null;
                    project.backgroundMode = detail.canvas_data?.backgroundMode ?? "lines";
                    project.showImageInfo = detail.canvas_data?.showImageInfo ?? false;
                    project.viewport = detail.canvas_data?.viewport ?? initialViewport;
                    set((state) => ({
                        projects: state.projects.some((item) => item.id === detail.id) ? state.projects.map((item) => (item.id === detail.id ? project : item)) : [project, ...state.projects],
                        cloudVersions: { ...state.cloudVersions, [detail.id]: detail.version },
                        cloudSyncStatus: "synced",
                    }));
                    return true;
                } catch {
                    set({ cloudSyncStatus: "error" });
                    return false;
                }
            },
            saveProjectToCloud: async (projectId, fetchImpl) => {
                if (!getStoredFloraToken()) {
                    set({ cloudSyncStatus: "local" });
                    return null;
                }
                const project = get().projects.find((item) => item.id === projectId);
                if (!project) return null;
                set({ cloudSyncStatus: "syncing" });
                try {
                    const snapshot = toCanvasDataSnapshot(project);
                    const detail = isCloudProjectId(projectId)
                        ? await updateCloudProject(projectId, { name: project.title, canvas_data: snapshot }, { fetchImpl })
                        : await createCloudProject({ name: project.title, canvas_data: snapshot }, { fetchImpl });
                    if (!isCloudProjectId(projectId)) {
                        // 本地 nanoid 项目首次上云：用云端 UUID 换掉本地 id。
                        set((state) => ({
                            projects: state.projects.map((item) => (item.id === projectId ? { ...item, id: detail.id, createdAt: detail.created_at, updatedAt: detail.updated_at } : item)),
                            cloudVersions: { ...state.cloudVersions, [detail.id]: detail.version },
                            cloudSyncStatus: "synced",
                        }));
                        return detail.id;
                    }
                    set((state) => ({ cloudVersions: { ...state.cloudVersions, [detail.id]: detail.version }, cloudSyncStatus: "synced" }));
                    return detail.id;
                } catch {
                    set({ cloudSyncStatus: "error" });
                    return null;
                }
            },
            deleteProjectsFromCloud: async (ids, fetchImpl) => {
                if (!getStoredFloraToken()) return;
                const cloudIds = ids.filter((id) => isCloudProjectId(id));
                if (!cloudIds.length) return;
                await Promise.all(cloudIds.map((id) => deleteCloudProject(id, { fetchImpl }).catch(() => undefined)));
                set((state) => {
                    const cloudVersions = { ...state.cloudVersions };
                    cloudIds.forEach((id) => delete cloudVersions[id]);
                    return { cloudVersions };
                });
            },
        }),
        {
            name: CANVAS_STORE_KEY,
            storage: canvasStorage,
            partialize: (state) =>
                ({
                    projects: state.projects,
                    deletedProjects: state.deletedProjects,
                }) as StorageValue<CanvasStore>["state"],
            onRehydrateStorage: () => () => {
                useCanvasStore.setState({ hydrated: true });
            },
        },
    ),
);
