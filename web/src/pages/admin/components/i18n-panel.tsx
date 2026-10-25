import { useCallback, useEffect, useState } from "react";
import { App, Button, Empty, Input, Modal, Select, Table } from "antd";
import { RefreshCw, Search } from "lucide-react";
import { useTranslation } from "react-i18next";

import { PilipalaApiError } from "@/services/api/pilipala-client";
import { adminUpsertI18nMessage, fetchAdminI18nMessages, type PilipalaI18nMessage } from "@/services/api/pilipala-admin";

type Draft = { key: string; locale: "zh" | "en"; template: string };

const EMPTY_DRAFT: Draft = { key: "", locale: "zh", template: "" };

/**
 * 运维面板：动态文案（i18n_message）在线热更新。
 *
 * <p>保存后后端立即生效，不需要重启 api。
 */
export function I18nPanel({ onForbidden }: { onForbidden: () => void }) {
    const { t } = useTranslation();
    const { message } = App.useApp();
    const [keyword, setKeyword] = useState("");
    const [messages, setMessages] = useState<PilipalaI18nMessage[]>([]);
    const [loading, setLoading] = useState(false);
    const [draft, setDraft] = useState<Draft | null>(null);
    const [saving, setSaving] = useState(false);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setMessages(await fetchAdminI18nMessages(keyword.trim() || undefined));
        } catch (error) {
            if (error instanceof PilipalaApiError && error.errorCode === "FORBIDDEN") {
                onForbidden();
                return;
            }
            message.error(t("admin.i18n.loadFailed"));
        } finally {
            setLoading(false);
        }
    }, [keyword, onForbidden, message, t]);

    useEffect(() => {
        void load();
    }, [load]);

    const save = async () => {
        if (!draft) return;
        setSaving(true);
        try {
            await adminUpsertI18nMessage({ key: draft.key.trim(), locale: draft.locale, template: draft.template.trim() });
            message.success(t("admin.i18n.saved"));
            setDraft(null);
            await load();
        } catch (error) {
            if (error instanceof PilipalaApiError && error.errorCode === "FORBIDDEN") {
                onForbidden();
                return;
            }
            message.error(error instanceof Error ? error.message : t("admin.loadFailed"));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-2">
                <Input
                    className="max-w-xs"
                    allowClear
                    prefix={<Search className="size-4 text-stone-400" />}
                    value={keyword}
                    placeholder={t("admin.i18n.search")}
                    onChange={(event) => setKeyword(event.target.value)}
                />
                <Button icon={<RefreshCw className="size-3.5" />} loading={loading} onClick={() => void load()}>
                    {t("common.refresh")}
                </Button>
                <Button type="primary" onClick={() => setDraft({ ...EMPTY_DRAFT })}>
                    {t("admin.i18n.add")}
                </Button>
            </div>
            <Table<PilipalaI18nMessage>
                rowKey="id"
                size="small"
                loading={loading}
                dataSource={messages}
                pagination={{ pageSize: 20, showSizeChanger: false, size: "small" }}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t("admin.i18n.empty")} /> }}
                columns={[
                    { title: t("admin.i18n.key"), dataIndex: "key", width: 260 },
                    { title: t("admin.i18n.locale"), dataIndex: "locale", width: 90 },
                    { title: t("admin.i18n.template"), dataIndex: "template", ellipsis: true },
                    {
                        title: t("admin.i18n.updatedAt"),
                        dataIndex: "updated_at",
                        width: 200,
                        render: (value: string) => new Date(value).toLocaleString(),
                    },
                    {
                        title: t("common.actions"),
                        width: 110,
                        render: (_, item) => (
                            <Button size="small" type="text" onClick={() => setDraft({ key: item.key, locale: item.locale, template: item.template })}>
                                {t("admin.i18n.edit")}
                            </Button>
                        ),
                    },
                ]}
            />

            <Modal
                open={Boolean(draft)}
                title={t("admin.i18n.edit")}
                onCancel={() => setDraft(null)}
                onOk={() => void save()}
                okText={t("common.save")}
                cancelText={t("common.cancel")}
                confirmLoading={saving}
                destroyOnHidden
            >
                {draft ? (
                    <div className="flex flex-col gap-3 py-2">
                        <Input value={draft.key} placeholder={t("admin.i18n.key")} onChange={(event) => setDraft({ ...draft, key: event.target.value })} />
                        <Select
                            className="w-32"
                            value={draft.locale}
                            options={[
                                { value: "zh", label: "zh" },
                                { value: "en", label: "en" },
                            ]}
                            onChange={(value) => setDraft({ ...draft, locale: value })}
                        />
                        <Input.TextArea rows={4} value={draft.template} placeholder={t("admin.i18n.template")} onChange={(event) => setDraft({ ...draft, template: event.target.value })} />
                    </div>
                ) : null}
            </Modal>
        </div>
    );
}
