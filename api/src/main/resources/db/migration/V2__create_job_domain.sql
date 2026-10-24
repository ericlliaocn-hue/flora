-- WP4 Job 域：job / job_step / outbox_message / idempotency_record。
-- 列与 docs/engineering/data-model/jobs.md 逐字一致。
--
-- 本迁移不建：provider_task（WP5）、wallet*（WP6）、job_lease（永远不建，用 Redis 短锁）。
-- hold_id 与 provider_task_id 故意不加物理 FK：被指向的表本 WP 还不存在。

CREATE TABLE job (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES users (id),
    status              TEXT        NOT NULL CHECK (status IN ('created', 'queued', 'running', 'succeeded', 'failed', 'cancelled', 'dead')),
    capability          TEXT        NOT NULL,
    model               TEXT        NOT NULL,
    params              JSONB       NOT NULL,
    attempt             INT         NOT NULL DEFAULT 0 CHECK (attempt >= 0),
    max_attempts        INT         NOT NULL DEFAULT 3 CHECK (max_attempts >= 1),
    hold_id             UUID,
    idempotency_key     TEXT        NOT NULL,
    cancel_requested_at TIMESTAMPTZ,
    error_code          TEXT,
    result_object_keys  JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT job_user_id_idempotency_key_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX job_user_id_created_at_idx ON job (user_id, created_at DESC);
CREATE INDEX job_status_updated_at_idx ON job (status, updated_at);

CREATE TABLE job_step (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id           UUID        NOT NULL REFERENCES job (id) ON DELETE CASCADE,
    attempt          INT         NOT NULL CHECK (attempt >= 1),
    status           TEXT        NOT NULL CHECK (status IN ('running', 'succeeded', 'failed', 'cancelled')),
    error_code       TEXT,
    provider_task_id UUID,
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at         TIMESTAMPTZ,
    CONSTRAINT job_step_job_id_attempt_key UNIQUE (job_id, attempt)
);

CREATE TABLE outbox_message (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type TEXT        NOT NULL,
    aggregate_id   UUID        NOT NULL,
    payload        JSONB       NOT NULL,
    status         TEXT        NOT NULL CHECK (status IN ('pending', 'sent', 'failed')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at        TIMESTAMPTZ
);

CREATE INDEX outbox_message_pending_idx ON outbox_message (status, created_at) WHERE status = 'pending';

CREATE TABLE idempotency_record (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id),
    "key"        TEXT        NOT NULL,
    job_id       UUID        NOT NULL REFERENCES job (id),
    request_hash TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT idempotency_record_user_id_key_key UNIQUE (user_id, "key")
);
