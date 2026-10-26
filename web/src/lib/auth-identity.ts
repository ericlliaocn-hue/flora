const CN_MOBILE = /^1[3-9]\d{9}$/;

/** 与后端 IdentityNormalizer 对齐：中国大陆手机号规范为 E.164（+86 + 11 位）。 */
export function normalizePhoneForApi(raw: string): string {
    const trimmed = raw.trim();
    const digits = trimmed.replace(/[\s-]/g, "");
    let national = digits;
    if (digits.startsWith("+86")) {
        national = digits.slice(3);
    } else if (digits.startsWith("86") && digits.length === 13) {
        national = digits.slice(2);
    }
    if (!CN_MOBILE.test(national)) {
        return trimmed;
    }
    return `+86${national}`;
}

/** 与后端 IdentityNormalizer 对齐：trim + 小写。 */
export function normalizeEmailForApi(raw: string): string {
    return raw.trim().toLowerCase();
}

export function authIdentityPayload(kind: "phone" | "email", identity: string) {
    return kind === "phone" ? { phone: normalizePhoneForApi(identity) } : { email: normalizeEmailForApi(identity) };
}

export function authOtpDestination(kind: "phone" | "email", identity: string) {
    return kind === "phone" ? normalizePhoneForApi(identity) : normalizeEmailForApi(identity);
}
