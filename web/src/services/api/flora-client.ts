import { API_BASE_URL } from "@/constant/env";
import i18n from "@/i18n";

/** 登录令牌在 localStorage 里的键：只是一个极小配置，允许用 localStorage。 */
export const FLORA_AUTH_TOKEN_KEY = "flora:auth_token";

/** Sa-Token 的请求头名，与 api 的 sa-token.token-name 保持一致。 */
export const FLORA_TOKEN_HEADER = "flora-token";

export type FloraApiEnvelope<T> = {
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
export class FloraApiError extends Error {
    readonly errorCode: string;

    constructor(errorCode: string, message: string) {
        super(message);
        this.name = "FloraApiError";
        this.errorCode = errorCode;
    }
}

export function getStoredFloraToken(): string | null {
    try {
        return globalThis.localStorage?.getItem(FLORA_AUTH_TOKEN_KEY) || null;
    } catch {
        return null;
    }
}

export function setStoredFloraToken(token: string | null) {
    try {
        if (token) globalThis.localStorage?.setItem(FLORA_AUTH_TOKEN_KEY, token);
        else globalThis.localStorage?.removeItem(FLORA_AUTH_TOKEN_KEY);
    } catch {
        // 隐私模式等场景 localStorage 不可用：忽略，登录态只留在内存里。
    }
}

type FloraRequestOptions = RequestInit & { token?: string | null; fetchImpl?: typeof fetch };

/**
 * 统一信封请求：解析 {ok, data, error_code, message}。
 *
 * <p>失败一律抛 {@link FloraApiError}；响应不是信封（例如网关没起返回 502 HTML）
 * 时也只给友好短句，绝不把 HTML 或上游原文吐到界面上。
 */
export async function floraRequest<T>(path: string, options?: FloraRequestOptions): Promise<T> {
    const { token, fetchImpl, headers, body, ...rest } = options ?? {};
    const impl = fetchImpl ?? globalThis.fetch;
    const requestHeaders: Record<string, string> = { Accept: "application/json", ...readHeaders(headers) };
    requestHeaders["Accept-Language"] = i18n.resolvedLanguage || i18n.language || "zh-CN";
    const authToken = token ?? getStoredFloraToken();
    if (authToken) requestHeaders[FLORA_TOKEN_HEADER] = authToken;
    if (body != null && !requestHeaders["Content-Type"]) requestHeaders["Content-Type"] = "application/json";

    const response = await impl(`${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`, { ...rest, headers: requestHeaders, body });
    const payload = await readJson(response);
    if (payload && typeof payload === "object" && typeof (payload as FloraApiEnvelope<T>).ok === "boolean") {
        const envelope = payload as FloraApiEnvelope<T>;
        if (envelope.ok) return envelope.data as T;
        throw new FloraApiError(envelope.error_code || "PROVIDER_UNAVAILABLE", envelope.message || i18n.t("canvas.projectPage.generationFailed"));
    }
    throw new FloraApiError("PROVIDER_UNAVAILABLE", i18n.t("canvas.projectPage.generationFailed"));
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
