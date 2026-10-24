-- WP11：Admin 运维与配置（users.role + model_display_config + FORBIDDEN 词条）
-- Ref: docs/engineering/data-model/README.md, docs/engineering/data-model/errors.md
--
-- 本迁移不改 V1–V6 任何已有迁移文件，只做增量。

-- 管理员角色：账号级只读运维权限，不引入第二套用户体系。
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'user'
    CHECK (role IN ('user', 'admin'));

-- 模型展示配置：只控制前端展示与上下架，不是渠道密钥表，也不是额度账本（ADR 0001）。
CREATE TABLE model_display_config (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    model        TEXT        NOT NULL,
    capability   TEXT        NOT NULL DEFAULT 'image.generate',
    display_name TEXT        NOT NULL,
    enabled      BOOLEAN     NOT NULL DEFAULT true,
    sort_order   INT         NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT model_display_config_model_key UNIQUE (model)
);

-- 真实 Anytoken 已开通的生图模型。
INSERT INTO model_display_config (model, capability, display_name, enabled, sort_order) VALUES
    ('gpt-image-2',           'image.generate', 'GPT Image 2',           true, 10),
    ('gpt-image-2.5-flare',   'image.generate', 'GPT Image 2.5 Flare',   true, 20),
    ('gpt-image-2.5-sunburst','image.generate', 'GPT Image 2.5 Sunburst',true, 30),
    ('image2',                'image.generate', 'Image 2',               true, 40)
ON CONFLICT (model) DO NOTHING;

-- 非管理员访问 /api/v1/admin/** 的友好文案。
INSERT INTO i18n_message (key, locale, template) VALUES
    ('FORBIDDEN',       'zh', '无权访问管理后台。'),
    ('FORBIDDEN',       'en', 'You do not have permission to access the admin console.'),
    ('error.FORBIDDEN', 'zh', '无权访问管理后台。'),
    ('error.FORBIDDEN', 'en', 'You do not have permission to access the admin console.')
ON CONFLICT (key, locale) DO NOTHING;
