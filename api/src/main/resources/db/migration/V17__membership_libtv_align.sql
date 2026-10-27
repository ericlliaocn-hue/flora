-- LibTV 对齐：会员周期价、Ultra 第三档 ultra_92、权益文案（中性描述）。

UPDATE membership_plan mp
SET benefits_json = v.benefits_json::jsonb,
    storage_gb = v.storage_gb,
    concurrency = v.concurrency
FROM (
    VALUES
        ('standard', 3, 2, '[{"zh":"每日登录送积分","en":"Daily login credits"},{"zh":"基础生图与编辑","en":"Core image tools"},{"zh":"画布项目本地保存","en":"Local canvas projects"}]'::text),
        ('plus', 10, 4, '[{"zh":"进阶生图队列优先","en":"Faster image queue"},{"zh":"更多并发任务","en":"Higher concurrency"},{"zh":"素材库容量提升","en":"More asset storage"}]'::text),
        ('pro', 30, NULL::int, '[{"zh":"工作流批量生成","en":"Batch workflows"},{"zh":"视频生成额度提升","en":"More video quota"},{"zh":"专属客服通道","en":"Priority support"}]'::text),
        ('deluxe', 100, NULL::int, '[{"zh":"专业级存储与并发","en":"Pro storage & concurrency"},{"zh":"高阶模型折扣权益","en":"Premium model discounts"},{"zh":"团队协作预备席位","en":"Team seats ready"}]'::text),
        ('ultra', 200, NULL::int, '[{"zh":"顶配积分与存储","en":"Top credits & storage"},{"zh":"全站模型权益最大化","en":"Maximum model perks"},{"zh":"定制方案对接","en":"Custom plan contact"}]'::text)
) AS v(code, storage_gb, concurrency, benefits_json)
WHERE mp.code = v.code;

UPDATE membership_plan_price mpp
SET credits_per_month = v.credits_per_month,
    price_fen = v.price_fen,
    list_price_fen = v.list_price_fen,
    renew_price_fen = v.renew_price_fen,
    gift_months = v.gift_months,
    badge_zh = v.badge_zh,
    sort_order = v.sort_order
FROM membership_plan mp,
(
    VALUES
        ('standard', 'year', 'default', 1500::bigint, 56900, 72900, 59900, 0, NULL::text, 10),
        ('plus', 'year', 'default', 4600::bigint, 119900, 219900, 179900, 0, NULL::text, 10),
        ('pro', 'year', 'default', 11700::bigint, 299900, 539900, 379900, 0, NULL::text, 10),
        ('pro', 'year', 'pro_alt', 16300::bigint, 399900, 739900, 509900, 0, NULL::text, 20),
        ('deluxe', 'year', 'default', 32800::bigint, 699900, 1499900, 739900, 0, '限时 47 折', 10),
        ('ultra', 'year', 'default', 50500::bigint, 1029900, 2299900, 1099900, 0, '限时 45 折', 10),
        ('ultra', 'year', 'ultra_alt', 66000::bigint, 1349900, 2999900, 1349900, 1, '限时 45 折', 20),
        ('standard', 'quarter', 'default', 1500::bigint, 17900, 18900, 18900, 0, NULL::text, 10),
        ('plus', 'quarter', 'default', 4600::bigint, 49900, 55900, 55900, 0, NULL::text, 10),
        ('pro', 'quarter', 'default', 11700::bigint, 119900, 139900, 139900, 0, NULL::text, 10),
        ('pro', 'quarter', 'pro_alt', 16300::bigint, 159900, 189900, 189900, 0, NULL::text, 20),
        ('deluxe', 'quarter', 'default', 32800::bigint, 269900, 349900, 349900, 0, '限时 77 折', 10),
        ('ultra', 'quarter', 'default', 50500::bigint, 399900, 529900, 529900, 0, '限时 74 折', 10),
        ('ultra', 'quarter', 'ultra_alt', 66000::bigint, 499900, 669900, 669900, 0, '限时 74 折', 20),
        ('standard', 'month', 'default', 1500::bigint, 6600, NULL::int, 6600, 0, NULL::text, 10),
        ('plus', 'month', 'default', 4600::bigint, 19900, NULL::int, 19900, 0, NULL::text, 10),
        ('pro', 'month', 'default', 11700::bigint, 46900, 48900, 46900, 0, NULL::text, 10),
        ('pro', 'month', 'pro_alt', 16300::bigint, 64900, 69900, 64900, 0, NULL::text, 20),
        ('deluxe', 'month', 'default', 32800::bigint, 119900, 129900, 119900, 0, '限时 92 折', 10),
        ('ultra', 'month', 'default', 50500::bigint, 179900, 199900, 179900, 0, '限时 9 折', 10),
        ('ultra', 'month', 'ultra_alt', 66000::bigint, 229900, 259900, 229900, 0, '限时 9 折', 20)
) AS v(plan_code, cycle, variant_code, credits_per_month, price_fen, list_price_fen, renew_price_fen, gift_months, badge_zh, sort_order)
WHERE mpp.plan_id = mp.id
  AND mp.code = v.plan_code
  AND mpp.cycle = v.cycle
  AND mpp.variant_code = v.variant_code;

-- Ultra 92K 档（包年 / 包季 / 包月）
INSERT INTO membership_plan_price (plan_id, cycle, variant_code, credits_per_month, price_fen, list_price_fen, renew_price_fen, badge_zh, gift_months, sort_order)
SELECT mp.id, v.cycle, v.variant_code, v.credits_per_month, v.price_fen, v.list_price_fen, v.renew_price_fen, v.badge_zh, v.gift_months, v.sort_order
FROM membership_plan mp
CROSS JOIN (
    VALUES
        ('year', 'ultra_92', 92000::bigint, 1829900, 4099900, 1829900, '限时 45 折', 1, 30),
        ('quarter', 'ultra_92', 92000::bigint, 669900, 899900, 669900, '限时 74 折', 0, 30),
        ('month', 'ultra_92', 92000::bigint, 319900, 359900, 319900, '限时 9 折', 0, 30)
) AS v(cycle, variant_code, credits_per_month, price_fen, list_price_fen, renew_price_fen, badge_zh, gift_months, sort_order)
WHERE mp.code = 'ultra'
  AND NOT EXISTS (
      SELECT 1
      FROM membership_plan_price existing
      WHERE existing.plan_id = mp.id
        AND existing.cycle = v.cycle
        AND existing.variant_code = v.variant_code
  );

-- V16 插入的 pro_alt / ultra_alt 包季包年若与上表不一致，再次对齐
UPDATE membership_plan_price mpp
SET credits_per_month = v.credits_per_month,
    price_fen = v.price_fen,
    list_price_fen = v.list_price_fen,
    renew_price_fen = v.renew_price_fen,
    gift_months = v.gift_months,
    badge_zh = v.badge_zh,
    sort_order = v.sort_order
FROM membership_plan mp,
(
    VALUES
        ('pro', 'quarter', 'pro_alt', 16300::bigint, 159900, 189900, 189900, 0, NULL::text, 20),
        ('pro', 'year', 'pro_alt', 16300::bigint, 399900, 739900, 509900, 0, NULL::text, 20),
        ('ultra', 'quarter', 'ultra_alt', 66000::bigint, 499900, 669900, 669900, 0, '限时 74 折', 20),
        ('ultra', 'year', 'ultra_alt', 66000::bigint, 1349900, 2999900, 1349900, 1, '限时 45 折', 20)
) AS v(plan_code, cycle, variant_code, credits_per_month, price_fen, list_price_fen, renew_price_fen, gift_months, badge_zh, sort_order)
WHERE mpp.plan_id = mp.id
  AND mp.code = v.plan_code
  AND mpp.cycle = v.cycle
  AND mpp.variant_code = v.variant_code;
