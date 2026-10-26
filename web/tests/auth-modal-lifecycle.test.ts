import { expect, test } from "bun:test";

import { shouldResetAuthModal } from "../src/components/auth/auth-modal-lifecycle";

test("opening the closed modal wipes fields and OTP cooldown", () => {
    expect(shouldResetAuthModal(true, false)).toBe(true);
});

test("switching login / register / forgot while open keeps identity and cooldown", () => {
    expect(shouldResetAuthModal(true, true)).toBe(false);
});

test("a closed modal never resets leftover form state", () => {
    expect(shouldResetAuthModal(false, true)).toBe(false);
    expect(shouldResetAuthModal(false, false)).toBe(false);
});
