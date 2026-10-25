import { useEffect } from "react";
import { App, Button, Form, Input, Modal, Tabs } from "antd";
import { useTranslation } from "react-i18next";

import { PilipalaApiError } from "@/services/api/pilipala-client";
import { useAuthStore } from "@/stores/use-auth-store";

/** 与后端 RegisterUser 的用户名规则一致：^[a-zA-Z0-9_]{3,32}$。 */
const USERNAME_PATTERN = /^[a-zA-Z0-9_]{3,32}$/;
const MIN_PASSWORD_LENGTH = 8;

type AuthFormValues = {
    username: string;
    password: string;
};

/**
 * 登录 / 注册弹窗。
 *
 * <p>失败提示直接用后端返回的本地化短句（WP9 的 i18n_message），
 * 不展示堆栈或上游原文。
 */
export function AuthModal() {
    const { t, i18n } = useTranslation();
    const { message } = App.useApp();
    const [form] = Form.useForm<AuthFormValues>();
    const open = useAuthStore((state) => state.authModalOpen);
    const tab = useAuthStore((state) => state.authModalTab);
    const loading = useAuthStore((state) => state.loading);
    const setAuthModalOpen = useAuthStore((state) => state.setAuthModalOpen);
    const login = useAuthStore((state) => state.login);
    const register = useAuthStore((state) => state.register);

    useEffect(() => {
        if (open) form.resetFields();
    }, [open, form]);

    const submit = async (values: AuthFormValues) => {
        try {
            if (tab === "login") {
                await login(values);
            } else {
                await register({ ...values, locale: i18n.resolvedLanguage === "en-US" ? "en" : "zh" });
            }
        } catch (error) {
            message.error(error instanceof PilipalaApiError ? error.message : t(tab === "login" ? "auth.loginFailed" : "auth.registerFailed"));
        }
    };

    return (
        <Modal open={open} onCancel={() => setAuthModalOpen(false)} footer={null} title={t("auth.title")} destroyOnHidden centered>
            <Tabs
                activeKey={tab}
                onChange={(key) => setAuthModalOpen(true, key as "login" | "register")}
                items={[
                    { key: "login", label: t("auth.login"), children: <AuthForm tab={tab} form={form} loading={loading} onSubmit={submit} /> },
                    { key: "register", label: t("auth.register"), children: <AuthForm tab={tab} form={form} loading={loading} onSubmit={submit} /> },
                ]}
            />
        </Modal>
    );
}

function AuthForm({ tab, form, loading, onSubmit }: { tab: "login" | "register"; form: ReturnType<typeof Form.useForm<AuthFormValues>>[0]; loading: boolean; onSubmit: (values: AuthFormValues) => void }) {
    const { t } = useTranslation();
    return (
        <Form form={form} layout="vertical" requiredMark={false} onFinish={onSubmit}>
            <p className="mb-4 text-xs text-stone-500 dark:text-stone-400">{tab === "login" ? t("auth.loginDescription") : t("auth.registerDescription")}</p>
            <Form.Item name="username" label={t("auth.username")} rules={[{ required: true, message: t("auth.usernamePlaceholder") }, { pattern: USERNAME_PATTERN, message: t("auth.usernamePlaceholder") }]}>
                <Input placeholder={t("auth.usernamePlaceholder")} autoComplete="username" />
            </Form.Item>
            <Form.Item
                name="password"
                label={t("auth.password")}
                rules={[{ required: true, message: t("auth.passwordPlaceholder") }, ...(tab === "register" ? [{ min: MIN_PASSWORD_LENGTH, message: t("auth.passwordPlaceholder") }] : [])]}
            >
                <Input.Password placeholder={t("auth.passwordPlaceholder")} autoComplete={tab === "login" ? "current-password" : "new-password"} />
            </Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading}>
                {tab === "login" ? t("auth.submitLogin") : t("auth.submitRegister")}
            </Button>
        </Form>
    );
}
