-- WP10: Cloud persistence domain (canvas_project + user_asset) and i18n error seeds
-- Ref: docs/engineering/data-model/projects.md, docs/engineering/data-model/errors.md

CREATE TABLE canvas_project (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id),
    name        TEXT        NOT NULL,
    canvas_data JSONB       NOT NULL DEFAULT '{}'::jsonb,
    version     BIGINT      NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_canvas_project_user_updated ON canvas_project (user_id, updated_at DESC);

CREATE TABLE user_asset (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id),
    project_id  UUID        NULL REFERENCES canvas_project(id) ON DELETE SET NULL,
    job_id      UUID        NULL REFERENCES job(id) ON DELETE SET NULL,
    source      TEXT        NOT NULL CHECK (source IN ('generated', 'uploaded')),
    object_key  TEXT        NOT NULL,
    mime_type   TEXT        NOT NULL DEFAULT 'image/png',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_asset_job_object UNIQUE (job_id, object_key)
);

CREATE INDEX idx_user_asset_user_created ON user_asset (user_id, created_at DESC);

INSERT INTO i18n_message (key, locale, template) VALUES
    ('PROJECT_NOT_FOUND',       'zh', '项目不存在。'),
    ('PROJECT_NOT_FOUND',       'en', 'The project does not exist.'),
    ('ASSET_NOT_FOUND',         'zh', '素材不存在。'),
    ('ASSET_NOT_FOUND',         'en', 'The asset does not exist.'),
    ('error.PROJECT_NOT_FOUND', 'zh', '项目不存在。'),
    ('error.PROJECT_NOT_FOUND', 'en', 'The project does not exist.'),
    ('error.ASSET_NOT_FOUND',   'zh', '素材不存在。'),
    ('error.ASSET_NOT_FOUND',   'en', 'The asset does not exist.')
ON CONFLICT (key, locale) DO NOTHING;
