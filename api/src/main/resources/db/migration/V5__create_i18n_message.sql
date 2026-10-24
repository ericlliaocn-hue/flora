-- WP9: i18n_message table and stage-1 seed templates
-- Ref: docs/engineering/data-model/errors.md, docs/engineering/i18n-and-errors.md
CREATE TABLE i18n_message (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    key        TEXT        NOT NULL,
    locale     TEXT        NOT NULL CHECK (locale IN ('zh', 'en')),
    template   TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT i18n_message_key_locale_key UNIQUE (key, locale)
);
INSERT INTO i18n_message (key, locale, template) VALUES
    ('WALLET_INSUFFICIENT',  'zh', '账户余额不足，请先充值。'),
    ('WALLET_INSUFFICIENT',  'en', 'Insufficient wallet balance. Please top up first.'),
    ('JOB_NOT_FOUND',        'zh', '任务不存在。'),
    ('JOB_NOT_FOUND',        'en', 'The task does not exist.'),
    ('JOB_CANCELLED',        'zh', '任务已取消。'),
    ('JOB_CANCELLED',        'en', 'The task has been cancelled.'),
    ('JOB_FAILED',           'zh', '任务执行失败。'),
    ('JOB_FAILED',           'en', 'The task failed to run.'),
    ('PROVIDER_UNAVAILABLE', 'zh', '生成服务暂时不可用，请稍后重试。'),
    ('PROVIDER_UNAVAILABLE', 'en', 'The generation service is temporarily unavailable. Please try again later.'),
    ('IDEMPOTENCY_CONFLICT', 'zh', '同一个幂等键对应了不同的请求内容。'),
    ('IDEMPOTENCY_CONFLICT', 'en', 'The same idempotency key was used with a different request.'),
    ('UNAUTHORIZED',         'zh', '请先登录后再试。'),
    ('UNAUTHORIZED',         'en', 'Please sign in first.'),
    ('VALIDATION_ERROR',     'zh', '请求参数不合法。'),
    ('VALIDATION_ERROR',     'en', 'The request parameters are invalid.'),
    ('JOB_FAILED.RETRY',     'zh', '生成服务异常，正在重试（第 {attempt}/{max_attempts} 次）。'),
    ('JOB_FAILED.RETRY',     'en', 'Generation issue, retrying ({attempt}/{max_attempts}).')
ON CONFLICT (key, locale) DO NOTHING;
