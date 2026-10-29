-- c7/c8 作为可选的更高画质档位；每个 SKU 一个 NewAPI group/model。
INSERT INTO model_product (code, capability, display_name, description, price_credits, param_schema, enabled, sort_order)
SELECT 'seedance-2-720p-quality', capability, 'Seedance 2 · 720p 高质',
       '4-15s / 720p / 9 图 + 3 视频 + 3 音频；c7 高质通道', 7000, param_schema, true, 11
FROM model_product WHERE code = 'seedance-2-720p-stable'
ON CONFLICT (code) DO NOTHING;

INSERT INTO model_product (code, capability, display_name, description, price_credits, param_schema, enabled, sort_order)
SELECT 'seedance-2-720p-pro', capability, 'Seedance 2 · 720p 旗舰',
       '4-15s / 720p / 9 图 + 3 视频 + 3 音频；c8 旗舰通道', 9000, param_schema, true, 12
FROM model_product WHERE code = 'seedance-2-720p-stable'
ON CONFLICT (code) DO NOTHING;

INSERT INTO model_product_group (product_id, group_code, model_code, priority, timeout_seconds, max_cost_amount, enabled)
SELECT p.id, 'default', 'sd-2-c7', 10, 900, 3.0, true
FROM model_product p WHERE p.code = 'seedance-2-720p-quality'
ON CONFLICT (product_id, priority) DO NOTHING;

INSERT INTO model_product_group (product_id, group_code, model_code, priority, timeout_seconds, max_cost_amount, enabled)
SELECT p.id, 'default', 'sd-2-c8', 10, 900, 4.0, true
FROM model_product p WHERE p.code = 'seedance-2-720p-pro'
ON CONFLICT (product_id, priority) DO NOTHING;
