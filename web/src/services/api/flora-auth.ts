import { floraRequest } from "./flora-client";

export type FloraUser = {
    user_id: string;
    username: string;
    locale: string | null;
    /** 角色：普通用户或运维管理员；顶栏据此决定是否展示后台入口。 */
    role?: "user" | "admin";
};

export type FloraAuthSession = FloraUser & {
    token: string;
};

export type FloraWallet = {
    user_id: string;
    available: number;
    frozen: number;
    updated_at: string;
};

export function registerUser(input: { username: string; password: string; locale?: "zh" | "en" }, fetchImpl?: typeof fetch) {
    return floraRequest<FloraAuthSession>("/api/v1/auth/register", {
        method: "POST",
        body: JSON.stringify({ username: input.username, password: input.password, ...(input.locale ? { locale: input.locale } : {}) }),
        fetchImpl,
    });
}

export function loginUser(input: { username: string; password: string }, fetchImpl?: typeof fetch) {
    return floraRequest<FloraAuthSession>("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: input.username, password: input.password }),
        fetchImpl,
    });
}

export function logoutUser(fetchImpl?: typeof fetch) {
    return floraRequest<Record<string, never>>("/api/v1/auth/logout", { method: "POST", fetchImpl });
}

export function fetchCurrentUser(fetchImpl?: typeof fetch) {
    return floraRequest<FloraUser>("/api/v1/auth/me", { fetchImpl });
}

export function fetchWallet(fetchImpl?: typeof fetch) {
    return floraRequest<FloraWallet>("/api/v1/wallet", { fetchImpl });
}
