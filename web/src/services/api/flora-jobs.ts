import { nanoid } from "nanoid";

import i18n from "@/i18n";
import { parseVideoResolution } from "@/lib/media-size";
import { FloraApiError, floraRequest } from "@/services/api/flora-client";
import { useAuthStore } from "@/stores/use-auth-store";
import { decodeChannelModel, isRemoteVideoModel, modelOptionName, type AiConfig } from "@/stores/use-config-store";
import type { GeneratedImageItem, ReferenceImage } from "@/types/image";

/** 后端 JobStatus 的小写形式：failed 是重试中的中间态，终态失败是 dead。 */
export type FloraJobStatus = "created" | "queued" | "running" | "succeeded" | "failed" | "cancelled" | "dead";

export type FloraSubmitJobRequest = {
    idempotency_key: string;
    capability: string;
    model: string;
    params: Record<string, unknown>;
    client_job_id?: string;
};

export type FloraSubmitJobView = {
    job_id: string;
    status: FloraJobStatus;
    hold_id: string | null;
};

export type FloraJobView = {
    job_id: string;
    status: FloraJobStatus;
    attempt: number;
    max_attempts: number;
    error_code: string | null;
    message: string | null;
    updated_at: string;
};

export type FloraJobOutput = {
    type: string;
    object_key: string;
    url: string | null;
};

export type FloraJobResultView = {
    job_id: string;
    status: FloraJobStatus;
    outputs: FloraJobOutput[];
    error_code: string | null;
    message: string | null;
};

export type FloraGeneratedImage = GeneratedImageItem;

type FloraJobCallOptions = { signal?: AbortSignal; fetchImpl?: typeof fetch };

export type RunFloraImageJobOptions = FloraJobCallOptions & {
    pollIntervalMs?: number;
    timeoutMs?: number;
    idempotencyKey?: string;
    clientJobId?: string;
    onStatusChange?: (job: FloraJobView) => void;
};

const DEFAULT_POLL_INTERVAL_MS = 800;
const DEFAULT_TIMEOUT_MS = 120_000;

export function submitFloraJob(payload: FloraSubmitJobRequest, options?: FloraJobCallOptions) {
    return floraRequest<FloraSubmitJobView>("/api/v1/jobs/submit", {
        method: "POST",
        body: JSON.stringify(payload),
        signal: options?.signal,
        fetchImpl: options?.fetchImpl,
    });
}

export function getFloraJob(jobId: string, options?: FloraJobCallOptions) {
    return floraRequest<FloraJobView>(`/api/v1/jobs/${encodeURIComponent(jobId)}`, { signal: options?.signal, fetchImpl: options?.fetchImpl });
}

export function getFloraJobResult(jobId: string, options?: FloraJobCallOptions) {
    return floraRequest<FloraJobResultView>(`/api/v1/jobs/${encodeURIComponent(jobId)}/result`, { signal: options?.signal, fetchImpl: options?.fetchImpl });
}

/**
 * 当前配置是不是走 flora 托管的视频通道（WP12）。
 *
 * <p>判定：托管云端模式，且当前视频模型没有绑定到某个本地渠道
 * （也就是 {@code channelId::modelName} 形式）。
 */
export function usesFloraManagedVideoChannel(config: AiConfig): boolean {
    return config.channelMode === "remote" && !decodeChannelModel(config.model || config.videoModel);
}

/** 托管视频通道要用的模型：只认 Seedance 托管模型，其余回落到 seedance2。 */
export function resolveFloraVideoModel(config: AiConfig): string {
    const model = modelOptionName(config.model || config.videoModel || "seedance2");
    return isRemoteVideoModel(model) ? model : "seedance2";
}

/**
 * 提交一个托管视频任务（WP12：capability = {@code video.generate}）。
 *
 * <p>只传已物化参数：提示词、比例、时长、清晰度。严禁把画布文档或参考素材
 * Base64 塞进 params（ADR 0003）。
 */
export async function submitFloraVideoJob(config: AiConfig, prompt: string, options?: { size?: string; seconds?: string; resolution?: string; aspectRatio?: string }): Promise<{ jobId: string; model: string }> {
    const model = resolveFloraVideoModel(config);
    const seconds = options?.seconds ?? config.videoSeconds;
    const submitted = await submitFloraJob({
        idempotency_key: nanoid(),
        capability: "video.generate",
        model,
        params: {
            prompt: prompt.trim(),
            size: options?.size ?? config.size,
            seconds,
            duration: Number(seconds) || 5,
            resolution: options?.resolution ?? `${parseVideoResolution(config.vquality)}p`,
            ...(options?.aspectRatio ? { aspect_ratio: options.aspectRatio } : {}),
        },
    });
    return { jobId: submitted.job_id, model };
}

export function cancelFloraJob(jobId: string, options?: FloraJobCallOptions) {
    return floraRequest<FloraJobView>(`/api/v1/jobs/${encodeURIComponent(jobId)}/cancel`, { method: "POST", signal: options?.signal, fetchImpl: options?.fetchImpl });
}

/**
 * 托管云端生图：submit → 轮询 query → 取 result。
 *
 * <p>失败文案一律用后端返回的本地化 message（WP9 的 i18n_message），
 * 不向界面暴露堆栈或上游英文原文（ADR 0005）。
 */
export async function runFloraImageJob(config: AiConfig, prompt: string, references?: ReferenceImage[], options?: RunFloraImageJobOptions): Promise<FloraGeneratedImage[]> {
    const model = modelOptionName(config.model || config.imageModel || "gpt-image-2") || "gpt-image-2";
    // 只传已物化参数：严禁把画布文档或参考图 Base64 塞进 params（ADR 0003）。
    const params: Record<string, unknown> = {
        prompt: prompt.trim(),
        size: config.size || "auto",
        quality: config.quality || "auto",
    };
    if (config.background) params.background = config.background;
    if (references?.length) params.reference_count = references.length;

    let submitted: FloraSubmitJobView;
    try {
        submitted = await submitFloraJob(
            {
                idempotency_key: options?.idempotencyKey || nanoid(),
                capability: "image.generate",
                model,
                params,
                ...(options?.clientJobId ? { client_job_id: options.clientJobId } : {}),
            },
            { signal: options?.signal, fetchImpl: options?.fetchImpl },
        );
    } catch (error) {
        if (error instanceof FloraApiError) {
            const auth = useAuthStore.getState();
            // 未登录：直接弹出登录框；余额不足：刷新顶栏余额。
            if (error.errorCode === "UNAUTHORIZED") auth.setAuthModalOpen(true, "login");
            if (error.errorCode === "WALLET_INSUFFICIENT") void auth.refreshWallet(options?.fetchImpl);
        }
        throw error;
    }

    const jobId = submitted.job_id;
    void useAuthStore.getState().refreshWallet(options?.fetchImpl);

    // 轮询期间用户点了停止：立刻取消后端任务，冻结额由后端退回。
    const cancelOnAbort = () => {
        void cancelFloraJob(jobId, { fetchImpl: options?.fetchImpl })
            .catch(() => undefined)
            .finally(() => void useAuthStore.getState().refreshWallet(options?.fetchImpl));
    };
    options?.signal?.addEventListener("abort", cancelOnAbort, { once: true });

    try {
        const job = await pollFloraJob(jobId, options);
        if (job.status === "cancelled") {
            void useAuthStore.getState().refreshWallet(options?.fetchImpl);
            throw new Error(job.message || i18n.t("common.requestCanceled"));
        }
        if (job.status === "dead") {
            void useAuthStore.getState().refreshWallet(options?.fetchImpl);
            throw new FloraApiError(job.error_code || "JOB_FAILED", job.message || i18n.t("canvas.projectPage.generationFailed"));
        }

        const result = await getFloraJobResult(jobId, { fetchImpl: options?.fetchImpl });
        void useAuthStore.getState().refreshWallet(options?.fetchImpl);
        const outputs = result.outputs.filter((output) => Boolean(output.url));
        if (!outputs.length) {
            throw new FloraApiError(result.error_code || "JOB_FAILED", result.message || i18n.t("canvas.projectPage.generationFailed"));
        }
        return await Promise.all(outputs.map((output) => loadImageFromUrl(output.url as string, options?.fetchImpl)));
    } finally {
        options?.signal?.removeEventListener("abort", cancelOnAbort);
    }
}

async function pollFloraJob(jobId: string, options?: RunFloraImageJobOptions) {
    const interval = options?.pollIntervalMs ?? DEFAULT_POLL_INTERVAL_MS;
    const deadline = Date.now() + (options?.timeoutMs ?? DEFAULT_TIMEOUT_MS);
    for (;;) {
        if (options?.signal?.aborted) throw canceledError();
        let job: FloraJobView;
        try {
            job = await getFloraJob(jobId, { signal: options?.signal, fetchImpl: options?.fetchImpl });
        } catch (error) {
            if (options?.signal?.aborted || isAbortError(error)) throw canceledError();
            throw error;
        }
        options?.onStatusChange?.(job);
        if (job.status === "succeeded" || job.status === "cancelled" || job.status === "dead") return job;
        if (Date.now() >= deadline) throw new FloraApiError("PROVIDER_UNAVAILABLE", i18n.t("canvas.projectPage.generationFailed"));
        await delay(interval, options?.signal);
    }
}

/**
 * 拉取 TOS 预签名地址里的图片。
 *
 * <p>本地文件桶没有 CORS 头时 fetch 会失败：回退成直链，
 * 由 image-storage 的 new Image() 加载宽高并直接渲染这个地址。
 */
async function loadImageFromUrl(url: string, fetchImpl?: typeof fetch): Promise<FloraGeneratedImage> {
    try {
        const response = await (fetchImpl ?? globalThis.fetch)(url);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const blob = await response.blob();
        return { id: nanoid(), dataUrl: await blobToDataUrl(blob), mimeType: blob.type || "image/png", bytes: blob.size };
    } catch {
        return { id: nanoid(), dataUrl: url, mimeType: "image/png", bytes: 0 };
    }
}

async function blobToDataUrl(blob: Blob): Promise<string> {
    if (typeof FileReader !== "undefined") {
        return await new Promise<string>((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(String(reader.result || ""));
            reader.onerror = () => reject(reader.error || new Error("read failed"));
            reader.readAsDataURL(blob);
        });
    }
    // 没有 FileReader 的环境（例如单测）：自己拼 base64。
    const buffer = new Uint8Array(await blob.arrayBuffer());
    let binary = "";
    for (let index = 0; index < buffer.length; index += 1) binary += String.fromCharCode(buffer[index]);
    return `data:${blob.type || "image/png"};base64,${btoa(binary)}`;
}

function delay(ms: number, signal?: AbortSignal) {
    return new Promise<void>((resolve, reject) => {
        if (signal?.aborted) {
            reject(canceledError());
            return;
        }
        const onAbort = () => {
            clearTimeout(timer);
            reject(canceledError());
        };
        const timer = setTimeout(() => {
            signal?.removeEventListener("abort", onAbort);
            resolve();
        }, ms);
        signal?.addEventListener("abort", onAbort, { once: true });
    });
}

function canceledError() {
    return new Error(i18n.t("common.requestCanceled"));
}

function isAbortError(error: unknown) {
    return error instanceof DOMException && error.name === "AbortError";
}
