-- 认证业务态友好文案：error_code 仍是 VALIDATION_ERROR，用独立 messageKey 区分。
INSERT INTO i18n_message (key, locale, template) VALUES
    ('AUTH_OTP_INVALID', 'zh', '验证码错误或已过期。'),
    ('AUTH_OTP_INVALID', 'en', 'Invalid or expired verification code.'),
    ('AUTH_ACCOUNT_NOT_FOUND', 'zh', '该账号尚未注册，请先注册。'),
    ('AUTH_ACCOUNT_NOT_FOUND', 'en', 'This account is not registered yet. Please sign up first.'),
    ('AUTH_IDENTITY_ALREADY_REGISTERED', 'zh', '该手机号或邮箱已注册，请直接登录。'),
    ('AUTH_IDENTITY_ALREADY_REGISTERED', 'en', 'This phone number or email is already registered. Please log in.')
ON CONFLICT (key, locale) DO NOTHING;
