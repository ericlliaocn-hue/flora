import { useCallback, useEffect, useState } from "react";
import { App, Button, Empty, Input, InputNumber, Space, Switch, Table } from "antd";
import { RefreshCw } from "lucide-react";
import { useTranslation } from "react-i18next";

import { FloraApiError } from "@/services/api/flora-client";
import { adminUpdateModel, fetchAdminModels, type FloraModelDisplayConfig } from "@/services/api/flora-admin";

type Draft = { display_name: string; sort_order: number };

/**
 * 运维面板：模型展示配置。
 *
 * <p>只管前端展示与上下架，不涉及任何供应商密钥。
 */
export function ModelsPanel({ onForbidden }: { onForbidden: () => void }) {
    const { t } = useTranslation();
    const { message } = App.useApp();
    const [models, setModels] = useState<FloraModelDisplayConfig[]>([]);
    const [loading, setLoading] = useState(false);
    const [drafts, setDrafts] = useState<Record<string, Draft>>({});

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const rows = await fetchAdminModels();
            setModels(rows);
            setDrafts(
                Object.fromEntries(rows.map((row) => [row.model, { display_name: row.display_name, sort_order: row.sort_order }])),
            );
        } catch (error) {
            if (error instanceof FloraApiError && error.errorCode === "FORBIDDEN") {
                onForbidden();
                return;
            }
            message.error(t("admin.models.loadFailed"));
        } finally {
            setLoading(false);
        }
    }, [onForbidden, message, t]);

    useEffect(() => {
        void load();
    }, [load]);

    const update = async (model: FloraModelDisplayConfig, enabled: boolean) => {
        const draft = drafts[model.model] ?? { display_name: model.display_name, sort_order: model.sort_order };
        try {
            await adminUpdateModel(model.model, {
                display_name: draft.display_name,
                capability: model.capability,
                enabled,
                sort_order: draft.sort_order,
            });
            message.success(t("admin.models.saved"));
            await load();
        } catch (error) {
            if (error instanceof FloraApiError && error.errorCode === "FORBIDDEN") {
                onForbidden();
                return;
            }
            message.error(error instanceof Error ? error.message : t("admin.loadFailed"));
        }
    };

    return (
        <div className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center gap-2">
                <Button icon={<RefreshCw className="size-3.5" />} loading={loading} onClick={() => void load()}>
                    {t("common.refresh")}
                </Button>
            </div>
            <Table<FloraModelDisplayConfig>
                rowKey="id"
                size="small"
                loading={loading}
                dataSource={models}
                pagination={{ pageSize: 20, showSizeChanger: false, size: "small" }}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t("admin.models.empty")} /> }}
                columns={[
                    { title: t("admin.models.model"), dataIndex: "model", width: 220 },
                    {
                        title: t("admin.models.displayName"),
                        dataIndex: "display_name",
                        width: 240,
                        render: (_value, model) => (
                            <Input
                                size="small"
                                value={drafts[model.model]?.display_name ?? model.display_name}
                                onChange={(event) => setDrafts({ ...drafts, [model.model]: { ...(drafts[model.model] ?? { sort_order: model.sort_order }), display_name: event.target.value } })}
                            />
                        ),
                    },
                    { title: t("admin.models.capability"), dataIndex: "capability", width: 180 },
                    {
                        title: t("admin.models.sortOrder"),
                        dataIndex: "sort_order",
                        width: 140,
                        render: (_value, model) => (
                            <InputNumber
                                size="small"
                                min={0}
                                precision={0}
                                value={drafts[model.model]?.sort_order ?? model.sort_order}
                                onChange={(value) => setDrafts({ ...drafts, [model.model]: { ...(drafts[model.model] ?? { display_name: model.display_name }), sort_order: value ?? 0 } })}
                            />
                        ),
                    },
                    {
                        title: t("admin.models.enabled"),
                        dataIndex: "enabled",
                        width: 100,
                        render: (enabled: boolean, model) => <Switch size="small" checked={enabled} onChange={(checked) => void update(model, checked)} />,
                    },
                    {
                        title: t("common.actions"),
                        width: 110,
                        render: (_, model) => (
                            <Space size={4}>
                                <Button size="small" type="text" onClick={() => void update(model, model.enabled)}>
                                    {t("admin.models.save")}
                                </Button>
                            </Space>
                        ),
                    },
                ]}
            />
        </div>
    );
}
