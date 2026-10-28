-- WP21：视频多渠道路由。新表 + job / job_step / provider_task 增量列。
-- 字段级以 docs/engineering/feature-briefs/2026-10-29-wp21-video-multi-channel-gateway.md 为准；
-- 实现 PR 已同步 data-model/jobs.md。
--
-- 设计约束（ADR 0008）：
--   - 用户/钱包/价格只在 pilipala；这三张表不是渠道密钥表、不是额度账本。
--   - 产品价固定；上游成本只做毛利分析。
--   - 存量 provider_task 行回填 legacy，调度器只扫 submitted / polling。

CREATE TABLE model_product (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code          TEXT        NOT NULL UNIQUE,
    capability    TEXT        NOT NULL,
    display_name  TEXT        NOT NULL,
    description   TEXT,
    price_credits BIGINT      NOT NULL CHECK (price_credits > 0),
    param_schema  JSONB       NOT NULL,
    enabled       BOOLEAN     NOT NULL DEFAULT true,
    sort_order    INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE model_target (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    code            TEXT        NOT NULL UNIQUE,
    gateway         TEXT        NOT NULL CHECK (gateway IN ('anytoken', 'newapi')),
    upstream_model  TEXT        NOT NULL,
    constraints     JSONB       NOT NULL,
    cost_type       TEXT        NOT NULL CHECK (cost_type IN ('per_call', 'per_second')),
    cost_amount     NUMERIC(12,4) NOT NULL CHECK (cost_amount >= 0),
    cost_currency   TEXT        NOT NULL DEFAULT 'USD',
    timeout_seconds INT         NOT NULL CHECK (timeout_seconds > 0),
    enabled         BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE model_route (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID        NOT NULL REFERENCES model_product (id) ON DELETE CASCADE,
    target_id       UUID        NOT NULL REFERENCES model_target (id) ON DELETE CASCADE,
    priority        INT         NOT NULL CHECK (priority >= 0),
    max_cost_amount NUMERIC(12,4),
    enabled         BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT model_route_product_priority_key UNIQUE (product_id, priority),
    CONSTRAINT model_route_product_target_key UNIQUE (product_id, target_id)
);

CREATE INDEX model_route_product_id_idx ON model_route (product_id, priority);

ALTER TABLE job
    ADD COLUMN product_id      UUID,
    ADD COLUMN target_id       UUID,
    ADD COLUMN quoted_price    BIGINT,
    ADD COLUMN pricing_version TEXT;

CREATE INDEX job_product_id_idx ON job (product_id);

ALTER TABLE job_step
    ADD COLUMN target_id   UUID,
    ADD COLUMN error_class TEXT;

ALTER TABLE provider_task
    ADD COLUMN target_id         UUID,
    ADD COLUMN upstream_task_id  TEXT,
    ADD COLUMN status            TEXT NOT NULL DEFAULT 'legacy'
        CHECK (status IN ('submitted', 'polling', 'succeeded', 'failed', 'cancelled', 'legacy')),
    ADD COLUMN next_poll_at      TIMESTAMPTZ,
    ADD COLUMN poll_count        INT NOT NULL DEFAULT 0 CHECK (poll_count >= 0),
    ADD COLUMN last_poll_at      TIMESTAMPTZ,
    ADD COLUMN error_class       TEXT,
    ADD COLUMN latency_ms        INT,
    ADD COLUMN cost_amount       NUMERIC(12,4),
    ADD COLUMN cost_currency     TEXT;

-- 存量 provider_task 行取 DEFAULT 'legacy'，不被 poll 调度器扫描；
-- NewAPI worker 新建行时显式写 submitted / polling。
CREATE INDEX provider_task_poll_idx ON provider_task (status, next_poll_at)
    WHERE status IN ('submitted', 'polling');

-- 种子：产品占位价 1000 credits，默认 disabled，运营确认价格后再开启；
-- target / route 先建立，便于 Phase 1 联调与 fallback 测试。
INSERT INTO model_product (code, capability, display_name, description, price_credits, param_schema, enabled, sort_order)
VALUES (
    'seedance-2-720p-stable',
    'video.generate',
    'Seedance 2 · 720p 稳定',
    '9 图 + 3 视频 + 3 音频 / 4-15s / 720p；c4 → c7 → c8',
    1000,
    $json$
    {
      "duration_s": { "min": 4, "max": 15 },
      "aspect_ratio": ["1:1", "16:9", "9:16"],
      "resolution": ["720p"],
      "reference_images": { "max": 9 },
      "reference_videos": { "max": 3 },
      "reference_audios": { "max": 3 },
      "generate_audio": true
    }
    $json$::jsonb,
    false,
    10
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO model_target (code, gateway, upstream_model, constraints, cost_type, cost_amount, cost_currency, timeout_seconds, enabled)
VALUES
    (
        'newapi/sd-2-c4',
        'newapi',
        'sd-2-c4',
        $json$
        {
          "duration_s": { "min": 4, "max": 15 },
          "aspect_ratio": ["1:1", "16:9", "9:16"],
          "resolution": ["720p"],
          "reference_images": { "max": 9 },
          "reference_videos": { "max": 3 },
          "reference_audios": { "max": 3 },
          "generate_audio": true
        }
        $json$::jsonb,
        'per_call',
        1.8000,
        'USD',
        900,
        true
    ),
    (
        'newapi/sd-2-c7',
        'newapi',
        'sd-2-c7',
        $json$
        {
          "duration_s": { "min": 4, "max": 15 },
          "aspect_ratio": ["1:1", "16:9", "9:16", "4:3", "3:4", "21:9"],
          "resolution": ["720p"],
          "reference_images": { "max": 9 },
          "reference_videos": { "max": 3 },
          "reference_audios": { "max": 3 },
          "generate_audio": true
        }
        $json$::jsonb,
        'per_call',
        2.8000,
        'USD',
        900,
        true
    ),
    (
        'newapi/sd-2-c8',
        'newapi',
        'sd-2-c8',
        $json$
        {
          "duration_s": { "min": 4, "max": 15 },
          "aspect_ratio": ["1:1", "16:9", "9:16", "4:3", "3:4", "21:9"],
          "resolution": ["720p"],
          "reference_images": { "max": 9 },
          "reference_videos": { "max": 3 },
          "reference_audios": { "max": 3 },
          "generate_audio": true
        }
        $json$::jsonb,
        'per_call',
        3.8000,
        'USD',
        900,
        true
    )
ON CONFLICT (code) DO NOTHING;

INSERT INTO model_route (product_id, target_id, priority, max_cost_amount)
SELECT p.id, t.id, v.priority, v.max_cost
FROM (
    VALUES
        ('seedance-2-720p-stable', 'newapi/sd-2-c4', 10, 2.0),
        ('seedance-2-720p-stable', 'newapi/sd-2-c7', 20, 3.0),
        ('seedance-2-720p-stable', 'newapi/sd-2-c8', 30, 4.0)
) AS v(product_code, target_code, priority, max_cost)
JOIN model_product p ON p.code = v.product_code
JOIN model_target t ON t.code = v.target_code
ON CONFLICT (product_id, priority) DO NOTHING;

-- WP21 新增错误码的文案种子（与 I18nDefaultCatalog 逐字一致）。
INSERT INTO i18n_message (key, locale, template) VALUES
    ('NO_AVAILABLE_CHANNEL', 'zh', '当前没有可用的视频线路，请稍后重试。'),
    ('NO_AVAILABLE_CHANNEL', 'en', 'No video channel is available right now. Please try again later.')
ON CONFLICT (key, locale) DO NOTHING;
