-- Pro / Ultra 包季、包年积分子 SKU（与包月 pro_alt / ultra_alt 对齐）；对已存在行幂等跳过。

INSERT INTO membership_plan_price (plan_id, cycle, variant_code, credits_per_month, price_fen, list_price_fen, renew_price_fen, badge_zh, sort_order)
SELECT mp.id, v.cycle, v.variant_code, v.credits_per_month, v.price_fen, v.list_price_fen, v.renew_price_fen, v.badge_zh, v.sort_order
FROM membership_plan mp
CROSS JOIN (
    VALUES
        ('pro', 'quarter', 'pro_alt', 16300::bigint, 179900, 204900, 179900, '8.8折', 20),
        ('pro', 'year', 'pro_alt', 16300::bigint, 639900, 819900, 639900, '7.8折', 20),
        ('ultra', 'quarter', 'ultra_alt', 66000::bigint, 639900, 779900, 639900, '8.3折', 20),
        ('ultra', 'year', 'ultra_alt', 66000::bigint, 2339900, 5199900, 2339900, '4.5折', 20)
) AS v(plan_code, cycle, variant_code, credits_per_month, price_fen, list_price_fen, renew_price_fen, badge_zh, sort_order)
WHERE mp.code = v.plan_code
  AND NOT EXISTS (
      SELECT 1
      FROM membership_plan_price existing
      WHERE existing.plan_id = mp.id
        AND existing.cycle = v.cycle
        AND existing.variant_code = v.variant_code
  );
