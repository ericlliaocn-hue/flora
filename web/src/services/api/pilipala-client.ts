import { API_BASE_URL } from "@/constant/env";
import i18n from "@/i18n";
import { readMigratedLocalStorageItem } from "@/lib/legacy-storage-migrate";

const LEGACY_AUTH_TOKEN_KEY = "flora:auth_token";

/** 登录令牌在 localStorage 里的键：只是一个极小配置，允许用 localStorage。 */
export const PILIPALA_AUTH_TOKEN_KEY = "pilipala:auth_token";

/** Sa-Token 请求头名，与 api 的 sa-token.token-name 默认一致。 */
export const PILIPALA_TOKEN_HEADER = "pilipala-token";

export type PilipalaApiEnvelope<T> = {
    ok: boolean;
    data: T | null;
    error_code: string | null;
    message: string | null;
};

/**
 * 后端返回的业务失败：只带稳定 error_code 与已本地化的友好短句。
 *
 * <p>上游原文、堆栈、SQL 一律不往这里带（ADR 0005）。
 */
export class PilipalaApiError extends Error {
    readonly errorCode: string;

    constructor(errorCode: string, message: string) {
        super(message);
        this.name = "PilipalaApiError";
        this.errorCode = errorCode;
    }
}

export function getStoredPilipalaToken(): string | null {
    return readMigratedLocalStorageItem(PILIPALA_AUTH_TOKEN_KEY, LEGACY_AUTH_TOKEN_KEY);
}

export function setStoredPilipalaToken(token: string | null) {
    try {
        if (token) globalThis.localStorage?.setItem(PILIPALA_AUTH_TOKEN_KEY, token);
        else globalThis.localStorage?.removeItem(PILIPALA_AUTH_TOKEN_KEY);
        globalThis.localStorage?.removeItem(LEGACY_AUTH_TOKEN_KEY);
    } catch {
        // 隐私模式等场景 localStorage 不可用：忽略，登录态只留在内存里。
    }
}

type PilipalaRequestOptions = RequestInit & { token?: string | null; fetchImpl?: typeof fetch };

/**
 * 统一信封请求：解析 {ok, data, error_code, message}。
 *
 * <p>失败一律抛 {@link PilipalaApiError}；响应不是信封（例如网关没起返回 502 HTML）
 * 时也只给友好短句，绝不把 HTML 或上游原文吐到界面上。
 */
export async function pilipalaRequest<T>(path: string, options?: PilipalaRequestOptions): Promise<T> {
    const { token, fetchImpl, headers, body, ...rest } = options ?? {};
    const impl = fetchImpl ?? globalThis.fetch;
    const requestHeaders: Record<string, string> = { Accept: "application/json", ...readHeaders(headers) };
    requestHeaders["Accept-Language"] = i18n.resolvedLanguage || i18n.language || "zh-CN";
    const authToken = token ?? getStoredPilipalaToken();
    if (authToken) requestHeaders[PILIPALA_TOKEN_HEADER] = authToken;
    if (body != null && !requestHeaders["Content-Type"]) requestHeaders["Content-Type"] = "application/json";

    const response = await impl(`${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`, { ...rest, headers: requestHeaders, body });
    const payload = await readJson(response);
    if (payload && typeof payload === "object" && typeof (payload as PilipalaApiEnvelope<T>).ok === "boolean") {
        const envelope = payload as PilipalaApiEnvelope<T>;
        if (envelope.ok) return envelope.data as T;
        throw new PilipalaApiError(envelope.error_code || "PROVIDER_UNAVAILABLE", envelope.message || i18n.t("canvas.projectPage.generationFailed"));
    }
    throw new PilipalaApiError("PROVIDER_UNAVAILABLE", i18n.t("canvas.projectPage.generationFailed"));
}

function readHeaders(headers: HeadersInit | undefined): Record<string, string> {
    if (!headers) return {};
    if (headers instanceof Headers) return Object.fromEntries(headers.entries());
    if (Array.isArray(headers)) return Object.fromEntries(headers);
    return { ...headers };
}

async function readJson(response: Response) {
    const text = await response.text();
    if (!text) return null;
    try {
        return JSON.parse(text);
    } catch {
        return null;
    }
}
