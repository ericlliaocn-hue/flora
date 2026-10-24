-- WP5：provider_task 原文留痕表 + job_step.provider_task_id 的物理外键。
-- 列与 docs/engineering/data-model/jobs.md 的 provider_task 逐字一致（共 7 列）。
--
-- 本迁移不改 V1 / V2 已有的表结构，只新增表、索引与一个外键约束。

CREATE TABLE provider_task (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id              UUID        NOT NULL REFERENCES job (id) ON DELETE CASCADE,
    job_step_id         UUID        REFERENCES job_step (id) ON DELETE SET NULL,
    upstream_request_id TEXT,
    raw_status          TEXT,
    raw_body            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX provider_task_job_id_idx ON provider_task (job_id);

-- job_step.provider_task_id 之前故意没有物理 FK（provider_task 表还不存在）。
ALTER TABLE job_step
    ADD CONSTRAINT job_step_provider_task_id_fkey
    FOREIGN KEY (provider_task_id) REFERENCES provider_task (id);
