import { useCallback, useEffect, useState } from "react";
import { App, Button, Empty, Modal, Select, Space, Table, Tag } from "antd";
import { RefreshCw } from "lucide-react";
import { useTranslation } from "react-i18next";

import { FloraApiError } from "@/services/api/flora-client";
import { adminRetryJob, fetchAdminJobs, type FloraAdminJob } from "@/services/api/flora-admin";

const JOB_STATUSES = ["dead", "failed", "running", "queued", "succeeded", "cancelled"] as const;

/**
 * 运维面板：任务与排障。
 *
 * <p>上游原文（raw_status / raw_body）只有这里能看到，C 端任务接口不返回它们。
 */
export function JobsPanel({ onForbidden }: { onForbidden: () => void }) {
    const { t } = useTranslation();
    const { message } = App.useApp();
    const [status, setStatus] = useState<string | undefined>(undefined);
    const [jobs, setJobs] = useState<FloraAdminJob[]>([]);
    const [loading, setLoading] = useState(false);
    const [detail, setDetail] = useState<FloraAdminJob | null>(null);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            setJobs(await fetchAdminJobs({ status, limit: 50 }));
        } catch (error) {
            if (error instanceof FloraApiError && error.errorCode === "FORBIDDEN") {
                onForbidden();
                return;
            }
            message.error(t("admin.jobs.loadFailed"));
        } finally {
            setLoading(false);
        }
    }, [status, onForbidden, message, t]);

    useEffect(() => {
        void load();
    }, [load]);

    const retry = async (job: FloraAdminJob) => {
        try {
            const updated = await adminRetryJob(job.job_id);
            message.success(t("admin.jobs.retryDone"));
            setDetail((current) => (current && current.job_id === updated.job_id ? updated : current));
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
                <Select
                    className="w-40"
                    allowClear
                    value={status}
                    placeholder={t("admin.jobs.status")}
                    options={[{ value: "", label: t("admin.jobs.all") }, ...JOB_STATUSES.map((value) => ({ value, label: value }))]}
                    onChange={(value) => setStatus(value || undefined)}
                />
                <Button icon={<RefreshCw className="size-3.5" />} loading={loading} onClick={() => void load()}>
                    {t("common.refresh")}
                </Button>
            </div>
            <Table<FloraAdminJob>
                rowKey="job_id"
                size="small"
                loading={loading}
                dataSource={jobs}
                pagination={{ pageSize: 20, showSizeChanger: false, size: "small" }}
                scroll={{ x: 1100 }}
                locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t("admin.jobs.empty")} /> }}
                columns={[
                    {
                        title: t("admin.jobs.jobId"),
                        dataIndex: "job_id",
                        width: 150,
                        render: (value: string) => <span className="font-mono text-xs">{value.slice(0, 8)}</span>,
                    },
                    { title: t("admin.jobs.user"), dataIndex: "username", width: 140 },
                    {
                        title: t("admin.jobs.status"),
                        dataIndex: "status",
                        width: 110,
                        render: (value: string) => <Tag color={value === "succeeded" ? "green" : value === "dead" || value === "failed" ? "red" : undefined}>{value}</Tag>,
                    },
                    { title: t("admin.jobs.model"), dataIndex: "model", width: 160 },
                    {
                        title: t("admin.jobs.prompt"),
                        dataIndex: "prompt",
                        ellipsis: true,
                        render: (value: string) => value || "-",
                    },
                    {
                        title: t("admin.jobs.attempt"),
                        dataIndex: "attempt",
                        width: 110,
                        render: (value: number, job) => `${value}/${job.max_attempts}`,
                    },
                    { title: t("admin.jobs.errorCode"), dataIndex: "error_code", width: 180, render: (value: string | null) => value ?? "-" },
                    {
                        title: t("admin.jobs.createdAt"),
                        dataIndex: "created_at",
                        width: 200,
                        render: (value: string) => new Date(value).toLocaleString(),
                    },
                    {
                        title: t("common.actions"),
                        width: 160,
                        render: (_, job) => (
                            <Space size={4}>
                                <Button size="small" type="text" onClick={() => setDetail(job)}>
                                    {t("admin.jobs.details")}
                                </Button>
                                {job.status === "dead" || job.status === "failed" ? (
                                    <Button size="small" type="text" onClick={() => void retry(job)}>
                                        {t("admin.jobs.retry")}
                                    </Button>
                                ) : null}
                            </Space>
                        ),
                    },
                ]}
            />

            <Modal open={Boolean(detail)} title={t("admin.jobs.details")} footer={null} onCancel={() => setDetail(null)} width={880} destroyOnHidden>
                <div className="flex flex-col gap-4">
                    <div className="text-xs text-stone-500">
                        <div className="font-mono">{detail?.job_id}</div>
                        <div className="mt-1">
                            {detail?.username} · {detail?.status} · {detail?.model}
                        </div>
                        {detail?.prompt ? <div className="mt-1">{detail.prompt}</div> : null}
                    </div>

                    <div>
                        <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-stone-400">{t("admin.jobs.steps")}</div>
                        <Table
                            rowKey="step_id"
                            size="small"
                            dataSource={detail?.steps ?? []}
                            pagination={false}
                            locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
                            columns={[
                                { title: t("admin.jobs.attempt"), dataIndex: "attempt", width: 80 },
                                { title: t("admin.jobs.status"), dataIndex: "status", width: 110 },
                                { title: t("admin.jobs.errorCode"), dataIndex: "error_code", render: (value: string | null) => value ?? "-" },
                                { title: t("admin.jobs.providerTasks"), dataIndex: "provider_task_id", render: (value: string | null) => value ?? "-" },
                            ]}
                        />
                    </div>

                    <div>
                        <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-stone-400">{t("admin.jobs.providerTasks")}</div>
                        <div className="flex flex-col gap-2">
                            {(detail?.provider_tasks ?? []).length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} /> : null}
                            {(detail?.provider_tasks ?? []).map((providerTask) => (
                                <div key={providerTask.id} className="rounded border border-stone-200 p-2 text-xs dark:border-stone-800">
                                    <div className="flex flex-wrap gap-3">
                                        <span>{t("admin.jobs.upstreamRequestId")}: {providerTask.upstream_request_id ?? "-"}</span>
                                        <span>
                                            {t("admin.jobs.rawStatus")}: {providerTask.raw_status ?? "-"}
                                        </span>
                                    </div>
                                    <pre className="mt-2 max-h-64 overflow-auto whitespace-pre-wrap break-all rounded bg-stone-100 p-2 text-[11px] dark:bg-stone-900">
                                        {formatRawBody(providerTask.raw_body)}
                                    </pre>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </Modal>
        </div>
    );
}

function formatRawBody(rawBody: unknown) {
    if (rawBody == null) return "-";
    if (typeof rawBody === "string") return rawBody;
    try {
        return JSON.stringify(rawBody, null, 2);
    } catch {
        return String(rawBody);
    }
}
