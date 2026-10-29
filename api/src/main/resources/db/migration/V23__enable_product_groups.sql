-- V19 在产品仍 disabled 时生成的 group 会继承 enabled=false；V20/V22 上架产品后必须同步 group。
UPDATE model_product_group g
SET enabled = p.enabled,
    updated_at = now()
FROM model_product p
WHERE g.product_id = p.id;
