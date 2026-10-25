import { create } from "zustand";

import { fetchCurrentUser, fetchWallet, loginUser, logoutUser, registerUser, type PilipalaUser, type PilipalaWallet } from "@/services/api/pilipala-auth";
import { PilipalaApiError, getStoredPilipalaToken, setStoredPilipalaToken } from "@/services/api/pilipala-client";

type AuthStore = {
    token: string | null;
    user: PilipalaUser | null;
    wallet: PilipalaWallet | null;
    loading: boolean;
    initialized: boolean;
    authModalOpen: boolean;
    authModalTab: "login" | "register";
    initialize: (fetchImpl?: typeof fetch) => Promise<void>;
    refreshWallet: (fetchImpl?: typeof fetch) => Promise<void>;
    login: (input: { username: string; password: string }, fetchImpl?: typeof fetch) => Promise<void>;
    register: (input: { username: string; password: string; locale?: "zh" | "en" }, fetchImpl?: typeof fetch) => Promise<void>;
    logout: (fetchImpl?: typeof fetch) => Promise<void>;
    setAuthModalOpen: (open: boolean, tab?: "login" | "register") => void;
};

export const useAuthStore = create<AuthStore>()((set, get) => ({
    token: getStoredPilipalaToken(),
    user: null,
    wallet: null,
    loading: false,
    initialized: false,
    authModalOpen: false,
    authModalTab: "login",

    initialize: async (fetchImpl) => {
        const token = getStoredPilipalaToken();
        if (!token) {
            set({ token: null, user: null, wallet: null, initialized: true });
            return;
        }
        set({ loading: true });
        try {
            const user = await fetchCurrentUser(fetchImpl);
            set({ token, user, initialized: true });
            await get().refreshWallet(fetchImpl);
        } catch (error) {
            // 令牌失效（UNAUTHORIZED）就清掉本地令牌，按未登录处理。
            if (error instanceof PilipalaApiError && error.errorCode === "UNAUTHORIZED") setStoredPilipalaToken(null);
            set({ token: null, user: null, wallet: null, initialized: true });
        } finally {
            set({ loading: false });
        }
    },

    refreshWallet: async (fetchImpl) => {
        if (!(get().token || getStoredPilipalaToken())) return;
        try {
            set({ wallet: await fetchWallet(fetchImpl) });
        } catch {
            // 余额刷新失败不影响主流程，静默忽略。
        }
    },

    login: async (input, fetchImpl) => {
        set({ loading: true });
        try {
            const session = await loginUser(input, fetchImpl);
            setStoredPilipalaToken(session.token);
            set({ token: session.token, user: session, authModalOpen: false });
            await get().refreshWallet(fetchImpl);
        } finally {
            set({ loading: false });
        }
    },

    register: async (input, fetchImpl) => {
        set({ loading: true });
        try {
            const session = await registerUser(input, fetchImpl);
            setStoredPilipalaToken(session.token);
            set({ token: session.token, user: session, authModalOpen: false });
            await get().refreshWallet(fetchImpl);
        } finally {
            set({ loading: false });
        }
    },

    logout: async (fetchImpl) => {
        try {
            await logoutUser(fetchImpl);
        } catch {
            // 登出失败也要清本地登录态。
        }
        setStoredPilipalaToken(null);
        set({ token: null, user: null, wallet: null });
    },

    setAuthModalOpen: (open, tab) => set((state) => ({ authModalOpen: open, authModalTab: tab ?? state.authModalTab })),
}));
