-- WP12：Seedance 视频模型种子 + 视频失败码的友好文案
-- Ref: docs/engineering/data-model/README.md, docs/engineering/data-model/errors.md
--
-- 本迁移不改 V1–V7 任何已有迁移文件，只做增量。

-- Anytoken Seedance 真实视频模型：只控制前端展示与上下架，不是渠道密钥表（ADR 0001）。
INSERT INTO model_display_config (model, capability, display_name, enabled, sort_order) VALUES
    ('seedance2',            'video.generate', 'Seedance 2',            true, 50),
    ('seedance2.5',          'video.generate', 'Seedance 2.5',          true, 60),
    ('seedance-fast',        'video.generate', 'Seedance Fast',         true, 70),
    ('seedance-mini',        'video.generate', 'Seedance Mini',         true, 80)
ON CONFLICT (model) DO NOTHING;

-- 视频链路的三个失败码：worker 会写进 job.error_code，
-- 必须先登记友好文案，否则 C 端 message 只能回显裸码（ADR 0005）。
INSERT INTO i18n_message (key, locale, template) VALUES
    ('GATEWAY_TIMEOUT',         'zh', '视频生成超时，请稍后重试。'),
    ('GATEWAY_TIMEOUT',         'en', 'The video generation timed out. Please try again later.'),
    ('GATEWAY_INVALID_RESPONSE', 'zh', '生成服务返回了无法识别的结果。'),
    ('GATEWAY_INVALID_RESPONSE', 'en', 'The generation service returned an unrecognized result.'),
    ('UPSTREAM_VIDEO_FAILED',   'zh', '视频生成失败，请调整提示词后重试。'),
    ('UPSTREAM_VIDEO_FAILED',   'en', 'The video generation failed. Please adjust the prompt and try again.')
ON CONFLICT (key, locale) DO NOTHING;
