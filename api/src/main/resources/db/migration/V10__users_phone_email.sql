-- WP17：手机 / 邮箱身份（phone、email 唯一；username 可空且不再用于登录）
-- Ref: docs/engineering/feature-briefs/2026-10-26-auth-phone-email-identity.md

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone TEXT,
    ADD COLUMN IF NOT EXISTS email TEXT;

ALTER TABLE users
    ALTER COLUMN username DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS users_phone_key ON users (phone) WHERE phone IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS users_email_key ON users (email) WHERE email IS NOT NULL;

-- 仅当不存在「phone 与 email 皆空」的存量行时加 CHECK（兼容仅 username 的旧测试账号）。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM users WHERE phone IS NULL AND email IS NULL
    ) THEN
        ALTER TABLE users
            ADD CONSTRAINT users_phone_or_email_check
            CHECK (phone IS NOT NULL OR email IS NOT NULL);
    END IF;
END $$;

-- WP17 新错误码与换绑占用文案（i18n_message 表已在 V5 建立）
INSERT INTO i18n_message (key, locale, template) VALUES
    ('OTP_RATE_LIMITED', 'zh', '验证码发送过于频繁，请稍后再试。'),
    ('OTP_RATE_LIMITED', 'en', 'Too many verification code requests. Please try again later.'),
    ('AUTH_IDENTITY_OCCUPIED', 'zh', '该手机号或邮箱已被其他账号绑定。'),
    ('AUTH_IDENTITY_OCCUPIED', 'en', 'This phone number or email is already bound to another account.')
ON CONFLICT (key, locale) DO NOTHING;
