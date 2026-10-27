-- WP20 Phase B：会员档、周期价、用户订阅；充值订单扩展 membership 种类。

CREATE TABLE membership_plan (
    id                BIGSERIAL PRIMARY KEY,
    code              TEXT        NOT NULL,
    name_zh           TEXT        NOT NULL,
    name_en           TEXT        NOT NULL,
    sort_order        INT         NOT NULL DEFAULT 0,
    credits_per_month BIGINT      NOT NULL CHECK (credits_per_month > 0),
    concurrency       INT,
    storage_gb        INT         NOT NULL DEFAULT 3,
    audience          TEXT        NOT NULL DEFAULT 'personal' CHECK (audience IN ('personal', 'team')),
    benefits_json     JSONB       NOT NULL DEFAULT '[]'::jsonb,
    enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT membership_plan_code_key UNIQUE (code)
);

CREATE TABLE membership_plan_price (
    id                 BIGSERIAL PRIMARY KEY,
    plan_id            BIGINT      NOT NULL REFERENCES membership_plan (id) ON DELETE CASCADE,
    cycle              TEXT        NOT NULL CHECK (cycle IN ('month', 'quarter', 'year')),
    variant_code       TEXT        NOT NULL DEFAULT 'default',
    credits_per_month  BIGINT      NOT NULL CHECK (credits_per_month > 0),
    price_fen          INT         NOT NULL CHECK (price_fen > 0),
    list_price_fen     INT         CHECK (list_price_fen IS NULL OR list_price_fen > 0),
    renew_price_fen    INT         CHECK (renew_price_fen IS NULL OR renew_price_fen > 0),
    badge_zh           TEXT,
    gift_months        INT         NOT NULL DEFAULT 0 CHECK (gift_months >= 0),
    sort_order         INT         NOT NULL DEFAULT 0,
    enabled            BOOLEAN     NOT NULL DEFAULT TRUE,
    meta_json          JSONB,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT membership_plan_price_uk UNIQUE (plan_id, cycle, variant_code)
);

CREATE INDEX membership_plan_price_enabled_idx ON membership_plan_price (enabled, cycle, sort_order);

CREATE TABLE user_subscription (
    id                     BIGSERIAL PRIMARY KEY,
    user_id                UUID        NOT NULL REFERENCES users (id),
    plan_id                BIGINT      NOT NULL REFERENCES membership_plan (id),
    plan_code              TEXT        NOT NULL,
    price_id               BIGINT      NOT NULL REFERENCES membership_plan_price (id),
    cycle                  TEXT        NOT NULL,
    variant_code           TEXT        NOT NULL DEFAULT 'default',
    status                 TEXT        NOT NULL CHECK (status IN ('active', 'expired', 'canceled')),
    started_at             TIMESTAMPTZ NOT NULL,
    current_period_start   TIMESTAMPTZ NOT NULL,
    current_period_end     TIMESTAMPTZ NOT NULL,
    seats                  INT         NOT NULL DEFAULT 1 CHECK (seats > 0),
    credits_per_month      BIGINT      NOT NULL,
    last_grant_at          TIMESTAMPTZ,
    source_order_id        UUID        REFERENCES recharge_order (id),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX user_subscription_user_active_idx ON user_subscription (user_id, status)
    WHERE status = 'active';

ALTER TABLE recharge_order
    ADD COLUMN order_kind TEXT NOT NULL DEFAULT 'recharge' CHECK (order_kind IN ('recharge', 'membership')),
    ADD COLUMN plan_price_id BIGINT REFERENCES membership_plan_price (id);

INSERT INTO app_runtime_setting (key, value) VALUES
    ('studio.enterprise.enabled', 'false')
ON CONFLICT (key) DO NOTHING;

-- 个人会员档（中性命名与示意价，运营可在 Admin 调整）
INSERT INTO membership_plan (code, name_zh, name_en, sort_order, credits_per_month, concurrency, storage_gb, audience, benefits_json) VALUES
    ('standard', '标准版', 'Standard', 10, 1500, 2, 3, 'personal', '[{"zh":"适合入门创作","en":"For getting started"}]'::jsonb),
    ('plus', '进阶版', 'Plus', 20, 4600, 4, 10, 'personal', '[{"zh":"日常创作","en":"Daily creation"}]'::jsonb),
    ('pro', '高级版', 'Pro', 30, 11700, NULL, 30, 'personal', '[{"zh":"工作场景","en":"Work use"}]'::jsonb),
    ('deluxe', '豪华版', 'Deluxe', 40, 32800, NULL, 100, 'personal', '[{"zh":"专业之选","en":"Professional"}]'::jsonb),
    ('ultra', '至尊版', 'Ultra', 50, 50500, NULL, 200, 'personal', '[{"zh":"全能创作","en":"All-in-one"}]'::jsonb);

INSERT INTO membership_plan_price (plan_id, cycle, variant_code, credits_per_month, price_fen, list_price_fen, renew_price_fen, badge_zh, sort_order) VALUES
    ((SELECT id FROM membership_plan WHERE code = 'standard'), 'month', 'default', 1500, 6600, NULL, 6600, NULL, 10),
    ((SELECT id FROM membership_plan WHERE code = 'standard'), 'quarter', 'default', 1500, 17900, 18900, 17900, '9.5折', 10),
    ((SELECT id FROM membership_plan WHERE code = 'standard'), 'year', 'default', 1500, 59900, 79900, 59900, '7.5折', 10),
    ((SELECT id FROM membership_plan WHERE code = 'plus'), 'month', 'default', 4600, 19900, NULL, 19900, NULL, 10),
    ((SELECT id FROM membership_plan WHERE code = 'plus'), 'quarter', 'default', 4600, 52900, 59900, 52900, '8.8折', 10),
    ((SELECT id FROM membership_plan WHERE code = 'plus'), 'year', 'default', 4600, 179900, 239900, 179900, '7.5折', 10),
    ((SELECT id FROM membership_plan WHERE code = 'pro'), 'month', 'default', 11700, 46900, 48900, 46900, NULL, 10),
    ((SELECT id FROM membership_plan WHERE code = 'pro'), 'month', 'pro_alt', 16300, 64900, 69900, 64900, NULL, 20),
    ((SELECT id FROM membership_plan WHERE code = 'pro'), 'quarter', 'default', 11700, 129900, 146900, 129900, '8.8折', 10),
    ((SELECT id FROM membership_plan WHERE code = 'pro'), 'year', 'default', 11700, 459900, 589900, 459900, '7.8折', 10),
    ((SELECT id FROM membership_plan WHERE code = 'deluxe'), 'month', 'default', 32800, 119900, 129900, 119900, NULL, 10),
    ((SELECT id FROM membership_plan WHERE code = 'deluxe'), 'quarter', 'default', 32800, 329900, 389900, 329900, '8.5折', 10),
    ((SELECT id FROM membership_plan WHERE code = 'deluxe'), 'year', 'default', 32800, 1099900, 1559900, 1099900, '7.0折', 10),
    ((SELECT id FROM membership_plan WHERE code = 'ultra'), 'month', 'default', 50500, 179900, 199900, 179900, NULL, 10),
    ((SELECT id FROM membership_plan WHERE code = 'ultra'), 'month', 'ultra_alt', 66000, 229900, 259900, 229900, NULL, 20),
    ((SELECT id FROM membership_plan WHERE code = 'ultra'), 'quarter', 'default', 50500, 499900, 599900, 499900, '8.3折', 10),
    ((SELECT id FROM membership_plan WHERE code = 'ultra'), 'year', 'default', 50500, 1829900, 4099900, 1829900, '4.5折', 10);
