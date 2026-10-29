-- 上架 Seedance 2 720p 产品。
-- 定价依据：c4 上游约 USD 1.8；按 pack 有效积分价值（约 ¥0.0044-0.0053/credit）
-- 5000 credits 约 ¥22-27，保留约 40-50% 毛利，覆盖支付/退款波动。
UPDATE model_product
SET price_credits = 5000,
    enabled = true,
    description = '4-15s / 720p / 9 图 + 3 视频 + 3 音频；c4 稳定通道',
    updated_at = now()
WHERE code = 'seedance-2-720p-stable';
