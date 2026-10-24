-- WP6 预付费钱包：wallet / wallet_hold / wallet_ledger。
-- 列与 docs/engineering/data-model/billing.md 逐字一致。
--
-- 金额一律 BIGINT（flora credit 整数），钱包三表只记用户余额（ADR 0001 单账本）：
-- 不建 NewAPI 风格的 tokens / quotas / channels 镜像表。
--
-- job.hold_id 的物理外键在本迁移末尾补齐（V2 时 wallet_hold 还不存在）。

CREATE TABLE wallet (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id),
    available  BIGINT      NOT NULL DEFAULT 0 CHECK (available >= 0),
    frozen     BIGINT      NOT NULL DEFAULT 0 CHECK (frozen >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT wallet_user_id_key UNIQUE (user_id)
);

CREATE TABLE wallet_hold (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users (id),
    job_id       UUID        UNIQUE REFERENCES job (id),
    amount       BIGINT      NOT NULL CHECK (amount > 0),
    status       TEXT        NOT NULL CHECK (status IN ('frozen', 'confirmed', 'released')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    finalized_at TIMESTAMPTZ
);

CREATE INDEX wallet_hold_user_id_created_at_idx ON wallet_hold (user_id, created_at);
CREATE INDEX wallet_hold_frozen_idx ON wallet_hold (created_at) WHERE status = 'frozen';

CREATE TABLE wallet_ledger (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id  UUID        NOT NULL REFERENCES wallet (id),
    user_id    UUID        NOT NULL REFERENCES users (id),
    type       TEXT        NOT NULL CHECK (type IN ('topup', 'freeze', 'confirm', 'release')),
    amount     BIGINT      NOT NULL CHECK (amount <> 0),
    hold_id    UUID        REFERENCES wallet_hold (id),
    job_id     UUID        REFERENCES job (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX wallet_ledger_wallet_id_created_at_idx ON wallet_ledger (wallet_id, created_at);

ALTER TABLE job
    ADD CONSTRAINT job_hold_id_fkey
    FOREIGN KEY (hold_id) REFERENCES wallet_hold (id);
