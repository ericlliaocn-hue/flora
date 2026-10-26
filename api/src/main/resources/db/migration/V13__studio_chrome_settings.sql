-- Studio 顶栏 / 活动条运营文案与开关（缺 key 时 API 仍用同表默认值）。
INSERT INTO app_runtime_setting (key, value) VALUES
    ('studio.promo.enabled', 'true'),
    ('studio.promo.end_at', '2026-11-15T15:59:59.000Z'),
    ('studio.promo.title', '预付费加赠限时开启'),
    ('studio.promo.copy', '充值加赠最高 20%，任务成功才扣费'),
    ('studio.promo.cta', '限时抢购'),
    ('studio.membership.badge', '限时 4.6 折'),
    ('studio.membership.enabled', 'true');
