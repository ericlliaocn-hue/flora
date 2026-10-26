CREATE TABLE app_runtime_setting (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO app_runtime_setting (key, value) VALUES
    ('auth.email_login_enabled', 'false'),
    ('auth.email_register_enabled', 'false');
