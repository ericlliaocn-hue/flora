import { pilipalaRequest } from "./pilipala-client";

export type PilipalaUser = {
    user_id: string;
    phone: string | null;
    email: string | null;
    locale: string | null;
    role?: "user" | "admin";
};

export type PilipalaAuthSession = PilipalaUser & {
    token: string;
};

export type PilipalaWallet = {
    user_id: string;
    available: number;
    frozen: number;
    updated_at: string;
};

export type OtpPurpose = "register" | "login" | "rebind_phone" | "rebind_email" | "reset_password";

export function sendAuthOtp(
    input: { channel: "sms" | "email"; destination: string; purpose: OtpPurpose },
    fetchImpl?: typeof fetch,
) {
    return pilipalaRequest<{ expires_in: number; cooldown: number }>("/api/v1/auth/otp/send", {
        method: "POST",
        body: JSON.stringify(input),
        fetchImpl,
    });
}

export function registerUser(
    input: { phone?: string; email?: string; password: string; otp: string; locale?: "zh" | "en" },
    fetchImpl?: typeof fetch,
) {
    return pilipalaRequest<PilipalaAuthSession>("/api/v1/auth/register", {
        method: "POST",
        body: JSON.stringify({
            ...(input.phone ? { phone: input.phone } : {}),
            ...(input.email ? { email: input.email } : {}),
            password: input.password,
            otp: input.otp,
            ...(input.locale ? { locale: input.locale } : {}),
        }),
        fetchImpl,
    });
}

export function loginUser(
    input: { phone?: string; email?: string; password?: string; otp?: string },
    fetchImpl?: typeof fetch,
) {
    return pilipalaRequest<PilipalaAuthSession>("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify({
            ...(input.phone ? { phone: input.phone } : {}),
            ...(input.email ? { email: input.email } : {}),
            ...(input.password ? { password: input.password } : {}),
            ...(input.otp ? { otp: input.otp } : {}),
        }),
        fetchImpl,
    });
}

export function logoutUser(fetchImpl?: typeof fetch) {
    return pilipalaRequest<Record<string, never>>("/api/v1/auth/logout", { method: "POST", fetchImpl });
}

export function fetchCurrentUser(fetchImpl?: typeof fetch) {
    return pilipalaRequest<PilipalaUser>("/api/v1/auth/me", { fetchImpl });
}

export function fetchWallet(fetchImpl?: typeof fetch) {
    return pilipalaRequest<PilipalaWallet>("/api/v1/wallet", { fetchImpl });
}

export function changePassword(input: { old_password: string; new_password: string }, fetchImpl?: typeof fetch) {
    return pilipalaRequest<Record<string, never>>("/api/v1/auth/password/change", {
        method: "POST",
        body: JSON.stringify(input),
        fetchImpl,
    });
}

export function resetPassword(
    input: { phone?: string; email?: string; otp: string; new_password: string },
    fetchImpl?: typeof fetch,
) {
    return pilipalaRequest<PilipalaAuthSession>("/api/v1/auth/password/reset", {
        method: "POST",
        body: JSON.stringify({
            ...(input.phone ? { phone: input.phone } : {}),
            ...(input.email ? { email: input.email } : {}),
            otp: input.otp,
            new_password: input.new_password,
        }),
        fetchImpl,
    });
}

export function rebindPhone(input: { new_phone: string; otp: string }, fetchImpl?: typeof fetch) {
    return pilipalaRequest<Record<string, never>>("/api/v1/auth/rebind/phone", {
        method: "POST",
        body: JSON.stringify(input),
        fetchImpl,
    });
}

export function rebindEmail(input: { new_email: string; otp: string }, fetchImpl?: typeof fetch) {
    return pilipalaRequest<Record<string, never>>("/api/v1/auth/rebind/email", {
        method: "POST",
        body: JSON.stringify(input),
        fetchImpl,
    });
}
