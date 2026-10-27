import { useCallback, useEffect, useState } from "react";
import { App, Button, Empty, Switch, Table } from "antd";
import { RefreshCw } from "lucide-react";
import { useTranslation } from "react-i18next";

import { PilipalaApiError } from "@/services/api/pilipala-client";
import { adminSetCreditProductEnabled, fetchAdminCreditProducts, type PilipalaAdminCreditProduct } from "@/services/api/pilipala-admin";

export function CreditProductsPanel({ onForbidden }: { onForbidden: () => void }) {
    const { t } = useTranslation();
    const { message } = App.useApp();
    const [rows, setRows] = useState<PilipalaAdminCreditProduct[]>([]);
    const [loading, setLoading] = useState(false);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setRows(await fetchAdminCreditProducts());
        } catch (error) {
            if (error instanceof PilipalaApiError && error.errorCode === "FORBIDDEN") {
                onForbidden();
                return;
            }
            message.error(t("admin.creditProducts.loadFailed"));
        } finally {
            setLoading(false);
        }
    }, [message, onForbidden, t]);

    useEffect(() => {
        void load();
    }, [load]);

    const toggle = async (row: PilipalaAdminCreditProduct, enabled: boolean) => {
        try {
            await adminSetCreditProductEnabled(row.id, enabled);
            message.success(t("admin.creditProducts.saved"));
            await load();
        } catch (error) {
            if (error instanceof PilipalaApiError && error.errorCode === "FORBIDDEN") {
                onForbidden();
                return;
            }
            message.error(error instanceof Error ? error.message : t("admin.loadFailed"));
        }
    };

    return (
        <div className="flex flex-col gap-3">
            <Button icon={<RefreshCw className="size-3.5" />} loading={loading} onClick={() => void load()}>
                {t("common.refresh")}
            </Button>
            <Table<PilipalaAdminCreditProduct>
                rowKey="id"
                size="small"
                loading={loading}
                dataSource={rows}
                pagination={{ pageSize: 20, showSizeChanger: false }}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t("admin.creditProducts.empty")} /> }}
                columns={[
                    { title: t("admin.creditProducts.code"), dataIndex: "code", width: 140 },
                    { title: t("admin.creditProducts.tab"), dataIndex: "tab", width: 100 },
                    { title: t("admin.creditProducts.name"), dataIndex: "name_zh" },
                    {
                        title: t("admin.creditProducts.price"),
                        render: (_, row) => `¥${(row.price_fen / 100).toFixed(2)}`,
                        width: 100,
                    },
                    { title: t("admin.creditProducts.credits"), dataIndex: "credits", width: 100 },
                    {
                        title: t("admin.creditProducts.enabled"),
                        width: 100,
                        render: (_, row) => (
                            <Switch checked={row.enabled} onChange={(checked) => void toggle(row, checked)} />
                        ),
                    },
                ]}
            />
        </div>
    );
}
