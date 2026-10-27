import { useEffect, useState } from "react";
import { Button, Spin, Tabs } from "antd";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

import { useAuthStore } from "@/stores/use-auth-store";
import { I18nPanel } from "./components/i18n-panel";
import { JobsPanel } from "./components/jobs-panel";
import { CreditProductsPanel } from "./components/credit-products-panel";
import { ModelsPanel } from "./components/models-panel";
import { UsersPanel } from "./components/users-panel";

/**
 * 运维后台（WP11 / M4）。
 *
 * <p>定位是运维与配置：查用户钱包、查任务与上游留痕、重投死信、在线改文案、模型上下架。
 * 这里不做第二套创作台，也不引入画布组件。
 *
 * <p>权限兜底：未登录提示登录；不是管理员（角色不是 admin，或接口回 FORBIDDEN）
 * 展示无权限提示。
 */
export default function AdminPage() {
    const { t } = useTranslation();
    const user = useAuthStore((state) => state.user);
    const initialized = useAuthStore((state) => state.initialized);
    const initialize = useAuthStore((state) => state.initialize);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);
    const [forbidden, setForbidden] = useState(false);

    useEffect(() => {
        if (!useAuthStore.getState().initialized) void initialize();
    }, [initialize]);

    if (!initialized) {
        return (
            <div className="flex h-full items-center justify-center bg-background">
                <Spin />
            </div>
        );
    }

    if (!user) {
        return (
            <div className="flex h-full flex-col items-center justify-center gap-3 bg-background px-6 text-center">
                <p className="text-sm text-stone-500 dark:text-stone-400">{t("admin.needLogin")}</p>
                <Button type="primary" onClick={() => setAuthModalOpen(true, "login")}>
                    {t("admin.login")}
                </Button>
            </div>
        );
    }

    if (forbidden || user.role !== "admin") {
        return (
            <div className="flex h-full flex-col items-center justify-center gap-3 bg-background px-6 text-center">
                <p className="text-sm text-stone-500 dark:text-stone-400">{t("admin.forbidden")}</p>
                <Link to="/">
                    <Button>{t("admin.backHome")}</Button>
                </Link>
            </div>
        );
    }

    return (
        <div className="flex h-full flex-col overflow-hidden bg-background">
            <div className="border-b border-stone-200 px-6 py-4 dark:border-stone-800">
                <h1 className="text-lg font-semibold text-stone-950 dark:text-stone-100">{t("admin.title")}</h1>
                <p className="mt-0.5 text-xs text-stone-500 dark:text-stone-400">{t("admin.subtitle")}</p>
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto px-6 py-4">
                <Tabs
                    items={[
                        { key: "users", label: t("admin.tabs.users"), children: <UsersPanel onForbidden={() => setForbidden(true)} /> },
                        { key: "jobs", label: t("admin.tabs.jobs"), children: <JobsPanel onForbidden={() => setForbidden(true)} /> },
                        { key: "i18n", label: t("admin.tabs.i18n"), children: <I18nPanel onForbidden={() => setForbidden(true)} /> },
                        { key: "models", label: t("admin.tabs.models"), children: <ModelsPanel onForbidden={() => setForbidden(true)} /> },
                        {
                            key: "creditProducts",
                            label: t("admin.tabs.creditProducts"),
                            children: <CreditProductsPanel onForbidden={() => setForbidden(true)} />,
                        },
                    ]}
                />
            </div>
        </div>
    );
}
