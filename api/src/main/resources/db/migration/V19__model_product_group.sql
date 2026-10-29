-- WP21 Phase 2：product → NewAPI group/model；替代 channel 级 model_target 作为 C 端路由。
CREATE TABLE model_product_group (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID        NOT NULL REFERENCES model_product (id) ON DELETE CASCADE,
    group_code      TEXT        NOT NULL,
    model_code      TEXT        NOT NULL,
    priority        INT         NOT NULL CHECK (priority >= 0),
    timeout_seconds INT         NOT NULL CHECK (timeout_seconds > 0),
    max_cost_amount NUMERIC(12,4),
    enabled         BOOLEAN     NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT model_product_group_product_priority_key UNIQUE (product_id, priority)
);

CREATE INDEX model_product_group_product_id_idx ON model_product_group (product_id, priority);

-- 为已有 NewAPI 产品取最低 priority 的 route 作为初始 group；group_code 固定 default，
-- model_code 取 target 的 upstream_model。旧 route/target 保留用于回滚。
INSERT INTO model_product_group
    (product_id, group_code, model_code, priority, timeout_seconds, max_cost_amount, enabled)
SELECT DISTINCT ON (r.product_id)
    r.product_id,
    'default',
    t.upstream_model,
    r.priority,
    t.timeout_seconds,
    r.max_cost_amount,
    (p.enabled AND r.enabled AND t.enabled)
FROM model_route r
JOIN model_target t ON t.id = r.target_id
JOIN model_product p ON p.id = r.product_id
WHERE t.gateway = 'newapi'
ORDER BY r.product_id, r.priority
ON CONFLICT DO NOTHING;

ALTER TABLE job
    ADD COLUMN gateway_group TEXT,
    ADD COLUMN gateway_model TEXT,
    ADD COLUMN gateway_timeout_seconds INT;
