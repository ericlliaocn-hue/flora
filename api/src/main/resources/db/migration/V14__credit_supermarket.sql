-- WP19 Phase A：积分超市 SKU、grant 到期批次、充值订单扩展、流水 expire 类型。

CREATE TABLE credit_product (
    id                              BIGSERIAL PRIMARY KEY,
    code                            TEXT        NOT NULL,
    tab                             TEXT        NOT NULL CHECK (tab IN ('surprise', 'general', 'model', 'custom')),
    name_zh                         TEXT        NOT NULL,
    name_en                         TEXT        NOT NULL,
    description_zh                  TEXT,
    description_en                  TEXT,
    price_fen                       INT         NOT NULL CHECK (price_fen > 0),
    list_price_fen                  INT         CHECK (list_price_fen IS NULL OR list_price_fen > 0),
    credits                         BIGINT      NOT NULL CHECK (credits >= 0),
    bonus_credits                   BIGINT      NOT NULL DEFAULT 0 CHECK (bonus_credits >= 0),
    first_purchase_bonus_credits    BIGINT      NOT NULL DEFAULT 0 CHECK (first_purchase_bonus_credits >= 0),
    first_purchase_bonus_percent    INT         CHECK (first_purchase_bonus_percent IS NULL OR first_purchase_bonus_percent >= 0),
    validity_days                   INT         CHECK (validity_days IS NULL OR validity_days > 0),
    min_plan_code                   TEXT,
    model_scope                     TEXT,
    sort_order                      INT         NOT NULL DEFAULT 0,
    enabled                         BOOLEAN     NOT NULL DEFAULT TRUE,
    badge_zh                        TEXT,
    badge_en                        TEXT,
    meta_json                       JSONB,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT credit_product_code_key UNIQUE (code)
);

CREATE INDEX credit_product_tab_enabled_sort_idx ON credit_product (tab, enabled, sort_order);

CREATE TABLE credit_grant (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            UUID        NOT NULL REFERENCES users (id),
    wallet_id          UUID        NOT NULL REFERENCES wallet (id),
    source_type        TEXT        NOT NULL CHECK (source_type IN ('recharge', 'bonus', 'admin', 'promo', 'subscription', 'daily_login')),
    source_id          TEXT        NOT NULL,
    grant_type         TEXT        NOT NULL CHECK (grant_type IN ('general', 'bonus', 'model', 'subscription', 'free')),
    credits_total      BIGINT      NOT NULL CHECK (credits_total > 0),
    credits_remaining  BIGINT      NOT NULL CHECK (credits_remaining >= 0),
    expires_at         TIMESTAMPTZ,
    product_id         BIGINT      REFERENCES credit_product (id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX credit_grant_user_expires_idx ON credit_grant (user_id, expires_at);
CREATE INDEX credit_grant_wallet_remaining_idx ON credit_grant (wallet_id, credits_remaining)
    WHERE credits_remaining > 0;

ALTER TABLE recharge_order
    ADD COLUMN product_id   BIGINT REFERENCES credit_product (id),
    ADD COLUMN product_code TEXT,
    ADD COLUMN bonus_credits BIGINT NOT NULL DEFAULT 0 CHECK (bonus_credits >= 0);

CREATE INDEX recharge_order_user_product_paid_idx ON recharge_order (user_id, product_code)
    WHERE status = 'paid' AND product_code IS NOT NULL;

ALTER TABLE wallet_ledger DROP CONSTRAINT IF EXISTS wallet_ledger_type_check;
ALTER TABLE wallet_ledger
    ADD CONSTRAINT wallet_ledger_type_check
    CHECK (type IN ('topup', 'freeze', 'confirm', 'release', 'refund', 'expire'));

-- 中性 SKU（价格由运营在 Admin 调整；此处为开发/演示默认值）
INSERT INTO credit_product (
    code, tab, name_zh, name_en, price_fen, list_price_fen, credits, bonus_credits,
    first_purchase_bonus_percent, validity_days, sort_order, enabled, badge_zh, badge_en, meta_json
) VALUES
    ('gen_light', 'general', '轻量创作包', 'Light pack', 4990, 6990, 7500, 0, 50, 730, 10, TRUE, '首购加赠 50%', 'First purchase +50%', NULL),
    ('gen_daily', 'general', '日常创作包', 'Daily pack', 8990, 11990, 15000, 0, 50, 730, 20, TRUE, '首购加赠 50%', 'First purchase +50%', NULL),
    ('gen_project', 'general', '项目启动包', 'Project pack', 15990, 21990, 30000, 0, 50, 730, 30, TRUE, '首购加赠 50%', 'First purchase +50%', NULL),
    ('gen_heavy', 'general', '重度创作包', 'Heavy pack', 39990, 59990, 90000, 0, 100, 730, 40, TRUE, '首购加赠 100%', 'First purchase +100%', NULL),
    ('surprise_flash', 'surprise', '灵感快充', 'Flash boost', 9990, NULL, 30000, 0, NULL, 30, 10, TRUE, NULL, NULL,
     '{"weekly_limit":1}'::jsonb),
    ('custom_slider', 'custom', '自定义充值', 'Custom top-up', 100, NULL, 0, 0, NULL, 730, 100, TRUE, NULL, NULL,
     '{"min_credits":600,"max_credits":499995,"credits_per_yuan":15}'::jsonb);
