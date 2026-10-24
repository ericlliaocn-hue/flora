-- WP3 认证与用户：只建 users 表。
-- 列与 docs/engineering/data-model/README.md 的「users（仅字段预留，实现 WP3）」逐字一致；
-- 不在本迁移里建 job / wallet / 其它表。

CREATE TABLE users (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username      TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    locale        TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX users_username_key ON users (username);
