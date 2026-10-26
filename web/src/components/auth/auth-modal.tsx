import { useEffect, useState } from "react";
import { App, Button, Form, Input, Modal, Radio, Tabs } from "antd";
import { useTranslation } from "react-i18next";

import { PilipalaLogo } from "@/components/brand/pilipala-logo";
import { PilipalaApiError } from "@/services/api/pilipala-client";
import { sendAuthOtp } from "@/services/api/pilipala-auth";
import { useAuthStore } from "@/stores/use-auth-store";

const MIN_PASSWORD_LENGTH = 8;

type AuthFormValues = {
    identity: string;
    password: string;
    otp: string;
};

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
    const [identityKind, setIdentityKind] = useState<"phone" | "email">("phone");
    const [loginMode, setLoginMode] = useState<"password" | "otp">("password");
    const [otpCooldown, setOtpCooldown] = useState(0);

    useEffect(() => {
        if (open) {
            form.resetFields();
            setOtpCooldown(0);
        }
    }, [open, form]);

    useEffect(() => {
        if (otpCooldown <= 0) return;
        const timer = window.setTimeout(() => setOtpCooldown((s) => s - 1), 1000);
        return () => window.clearTimeout(timer);
    }, [otpCooldown]);

    const identityPayload = (identity: string) =>
        identityKind === "phone" ? { phone: identity } : { email: identity };

    const sendOtp = async () => {
        const identity = form.getFieldValue("identity")?.trim();
        if (!identity) {
            message.warning(t("auth.identityPlaceholder"));
            return;
        }
        try {
            const purpose = tab === "register" ? "register" : "login";
            const result = await sendAuthOtp({
                channel: identityKind === "phone" ? "sms" : "email",
                destination: identity,
                purpose,
            });
            setOtpCooldown(result.cooldown);
            message.success(t("auth.otpSent"));
        } catch (error) {
            message.error(error instanceof PilipalaApiError ? error.message : t("auth.otpSendFailed"));
        }
    };

    const submit = async (values: AuthFormValues) => {
        try {
            if (tab === "login") {
                if (loginMode === "password") {
                    await login({ ...identityPayload(values.identity), password: values.password });
                } else {
                    await login({ ...identityPayload(values.identity), otp: values.otp });
                }
            } else {
                await register({
                    ...identityPayload(values.identity),
                    password: values.password,
                    otp: values.otp,
                    locale: i18n.resolvedLanguage === "en-US" ? "en" : "zh",
                });
            }
        } catch (error) {
            message.error(error instanceof PilipalaApiError ? error.message : t(tab === "login" ? "auth.loginFailed" : "auth.registerFailed"));
        }
    };

    return (
        <Modal
            open={open}
            onCancel={() => setAuthModalOpen(false)}
            footer={null}
            title={
                <span className="flex flex-col items-center gap-3 pt-1">
                    <PilipalaLogo variant="lockup" className="h-[72px] w-[100px] text-stone-900 dark:text-stone-100" />
                    <span>{t("auth.title")}</span>
                </span>
            }
            destroyOnHidden
            centered
        >
            <Tabs
                activeKey={tab}
                onChange={(key) => setAuthModalOpen(true, key as "login" | "register")}
                items={[
                    {
                        key: "login",
                        label: t("auth.login"),
                        children: (
                            <AuthForm
                                tab="login"
                                form={form}
                                loading={loading}
                                identityKind={identityKind}
                                loginMode={loginMode}
                                otpCooldown={otpCooldown}
                                onIdentityKindChange={setIdentityKind}
                                onLoginModeChange={setLoginMode}
                                onSendOtp={sendOtp}
                                onSubmit={submit}
                            />
                        ),
                    },
                    {
                        key: "register",
                        label: t("auth.register"),
                        children: (
                            <AuthForm
                                tab="register"
                                form={form}
                                loading={loading}
                                identityKind={identityKind}
                                loginMode="password"
                                otpCooldown={otpCooldown}
                                onIdentityKindChange={setIdentityKind}
                                onLoginModeChange={setLoginMode}
                                onSendOtp={sendOtp}
                                onSubmit={submit}
                            />
                        ),
                    },
                ]}
            />
        </Modal>
    );
}

function AuthForm({
    tab,
    form,
    loading,
    identityKind,
    loginMode,
    otpCooldown,
    onIdentityKindChange,
    onLoginModeChange,
    onSendOtp,
    onSubmit,
}: {
    tab: "login" | "register";
    form: ReturnType<typeof Form.useForm<AuthFormValues>>[0];
    loading: boolean;
    identityKind: "phone" | "email";
    loginMode: "password" | "otp";
    otpCooldown: number;
    onIdentityKindChange: (k: "phone" | "email") => void;
    onLoginModeChange: (m: "password" | "otp") => void;
    onSendOtp: () => void;
    onSubmit: (values: AuthFormValues) => void;
}) {
    const { t } = useTranslation();
    return (
        <Form form={form} layout="vertical" requiredMark={false} onFinish={onSubmit}>
            <p className="mb-4 text-xs text-stone-500 dark:text-stone-400">{tab === "login" ? t("auth.loginDescription") : t("auth.registerDescription")}</p>
            <Form.Item label={t("auth.identityKind")}>
                <Radio.Group value={identityKind} onChange={(e) => onIdentityKindChange(e.target.value)}>
                    <Radio value="phone">{t("auth.phone")}</Radio>
                    <Radio value="email">{t("auth.email")}</Radio>
                </Radio.Group>
            </Form.Item>
            {tab === "login" ? (
                <Form.Item label={t("auth.loginMethod")}>
                    <Radio.Group value={loginMode} onChange={(e) => onLoginModeChange(e.target.value)}>
                        <Radio value="password">{t("auth.passwordLogin")}</Radio>
                        <Radio value="otp">{t("auth.otpLogin")}</Radio>
                    </Radio.Group>
                </Form.Item>
            ) : null}
            <Form.Item
                name="identity"
                label={identityKind === "phone" ? t("auth.phone") : t("auth.email")}
                rules={[{ required: true, message: t("auth.identityPlaceholder") }]}
            >
                <Input
                    placeholder={identityKind === "phone" ? t("auth.phonePlaceholder") : t("auth.emailPlaceholder")}
                    autoComplete={identityKind === "phone" ? "tel" : "email"}
                />
            </Form.Item>
            {(tab === "register" || loginMode === "password") && (
                <Form.Item
                    name="password"
                    label={t("auth.password")}
                    rules={[
                        { required: true, message: t("auth.passwordPlaceholder") },
                        ...(tab === "register" || loginMode === "password" ? [{ min: MIN_PASSWORD_LENGTH, message: t("auth.passwordPlaceholder") }] : []),
                    ]}
                >
                    <Input.Password placeholder={t("auth.passwordPlaceholder")} autoComplete={tab === "login" ? "current-password" : "new-password"} />
                </Form.Item>
            )}
            {(tab === "register" || loginMode === "otp") && (
                <Form.Item name="otp" label={t("auth.otp")} rules={[{ required: true, message: t("auth.otpPlaceholder") }]}>
                    <Input
                        placeholder={t("auth.otpPlaceholder")}
                        addonAfter={
                            <Button type="link" size="small" disabled={otpCooldown > 0} onClick={onSendOtp}>
                                {otpCooldown > 0 ? t("auth.otpResend", { seconds: otpCooldown }) : t("auth.sendOtp")}
                            </Button>
                        }
                    />
                </Form.Item>
            )}
            <Button type="primary" htmlType="submit" block loading={loading}>
                {tab === "login" ? t("auth.submitLogin") : t("auth.submitRegister")}
            </Button>
        </Form>
    );
}
