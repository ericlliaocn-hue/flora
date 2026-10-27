import { useCallback, useEffect, useState } from "react";
import { App, Button, Empty, Switch, Table } from "antd";
import { RefreshCw } from "lucide-react";
import { useTranslation } from "react-i18next";

import { PilipalaApiError } from "@/services/api/pilipala-client";
import {
    adminSetMembershipPlanEnabled,
    adminSetMembershipPlanPriceEnabled,
    fetchAdminMembershipPlanPrices,
    fetchAdminMembershipPlans,
    type PilipalaAdminMembershipPlan,
    type PilipalaAdminMembershipPlanPrice,
} from "@/services/api/pilipala-admin";

export function MembershipPlansPanel({ onForbidden }: { onForbidden: () => void }) {
    const { t } = useTranslation();
    const { message } = App.useApp();
    const [plans, setPlans] = useState<PilipalaAdminMembershipPlan[]>([]);
    const [prices, setPrices] = useState<PilipalaAdminMembershipPlanPrice[]>([]);
    const [loading, setLoading] = useState(false);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [planRows, priceRows] = await Promise.all([fetchAdminMembershipPlans(), fetchAdminMembershipPlanPrices()]);
            setPlans(planRows);
            setPrices(priceRows);
        } catch (error) {
            if (error instanceof PilipalaApiError && error.errorCode === "FORBIDDEN") {
                onForbidden();
                return;
            }
            message.error(t("admin.membershipPlans.loadFailed"));
        } finally {
            setLoading(false);
        }
    }, [message, onForbidden, t]);

    useEffect(() => {
        void load();
    }, [load]);

    const togglePlan = async (row: PilipalaAdminMembershipPlan, enabled: boolean) => {
        try {
            await adminSetMembershipPlanEnabled(row.id, enabled);
            message.success(t("admin.membershipPlans.saved"));
            await load();
        } catch (error) {
            if (error instanceof PilipalaApiError && error.errorCode === "FORBIDDEN") {
                onForbidden();
                return;
            }
            message.error(error instanceof Error ? error.message : t("admin.loadFailed"));
        }
    };

    const togglePrice = async (row: PilipalaAdminMembershipPlanPrice, enabled: boolean) => {
        try {
            await adminSetMembershipPlanPriceEnabled(row.id, enabled);
            message.success(t("admin.membershipPlans.saved"));
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
        <div className="flex flex-col gap-4">
            <Button icon={<RefreshCw className="size-3.5" />} loading={loading} onClick={() => void load()}>
                {t("common.refresh")}
            </Button>
            <Table<PilipalaAdminMembershipPlan>
                rowKey="id"
                size="small"
                loading={loading}
                dataSource={plans}
                pagination={{ pageSize: 10, showSizeChanger: false }}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t("admin.membershipPlans.empty")} /> }}
                columns={[
                    { title: t("admin.membershipPlans.code"), dataIndex: "code", width: 120 },
                    { title: t("admin.membershipPlans.name"), dataIndex: "name_zh" },
                    { title: t("admin.membershipPlans.credits"), dataIndex: "credits_per_month", width: 120 },
                    {
                        title: t("admin.membershipPlans.enabled"),
                        width: 100,
                        render: (_, row) => <Switch checked={row.enabled} onChange={(checked) => void togglePlan(row, checked)} />,
                    },
                ]}
            />
            <Table<PilipalaAdminMembershipPlanPrice>
                rowKey="id"
                size="small"
                loading={loading}
                dataSource={prices}
                pagination={{ pageSize: 15, showSizeChanger: false }}
                columns={[
                    { title: t("admin.membershipPlans.planId"), dataIndex: "plan_id", width: 80 },
                    { title: t("admin.membershipPlans.cycle"), dataIndex: "cycle", width: 90 },
                    { title: t("admin.membershipPlans.variant"), dataIndex: "variant_code", width: 100 },
                    {
                        title: t("admin.membershipPlans.price"),
                        render: (_, row) => `¥${(row.price_fen / 100).toFixed(2)}`,
                        width: 100,
                    },
                    {
                        title: t("admin.membershipPlans.enabled"),
                        width: 100,
                        render: (_, row) => <Switch checked={row.enabled} onChange={(checked) => void togglePrice(row, checked)} />,
                    },
                ]}
            />
        </div>
    );
}
