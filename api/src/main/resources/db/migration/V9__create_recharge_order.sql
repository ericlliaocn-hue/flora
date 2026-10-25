-- WP16：充值订单 + wallet_ledger 追溯列与 refund 类型。

CREATE TABLE recharge_order (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID        NOT NULL REFERENCES users (id),
    out_trade_no       TEXT        NOT NULL,
    provider           TEXT        NOT NULL DEFAULT 'zpay',
    provider_trade_no  TEXT,
    package_id         TEXT        NOT NULL,
    money_fen          BIGINT      NOT NULL CHECK (money_fen > 0),
    credits            BIGINT      NOT NULL CHECK (credits > 0),
    status             TEXT        NOT NULL CHECK (status IN (
        'created', 'pending_pay', 'paid', 'failed', 'closed', 'refunded'
    )),
    client_ip          TEXT        NOT NULL,
    device             TEXT,
    pay_url            TEXT,
    qrcode_url         TEXT,
    product_name       TEXT        NOT NULL,
    ledger_id          UUID,
    paid_at            TIMESTAMPTZ,
    refunded_at        TIMESTAMPTZ,
    fail_reason        TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX recharge_order_out_trade_no_key ON recharge_order (out_trade_no);
CREATE INDEX recharge_order_user_id_created_at_idx ON recharge_order (user_id, created_at DESC);
CREATE INDEX recharge_order_status_created_at_idx ON recharge_order (status, created_at);
CREATE INDEX recharge_order_provider_trade_no_idx ON recharge_order (provider_trade_no)
    WHERE provider_trade_no IS NOT NULL;

ALTER TABLE wallet_ledger
    ADD COLUMN recharge_order_id UUID NULL;

CREATE INDEX wallet_ledger_recharge_order_id_idx
    ON wallet_ledger (recharge_order_id)
    WHERE recharge_order_id IS NOT NULL;

ALTER TABLE wallet_ledger DROP CONSTRAINT IF EXISTS wallet_ledger_type_check;
ALTER TABLE wallet_ledger
    ADD CONSTRAINT wallet_ledger_type_check
    CHECK (type IN ('topup', 'freeze', 'confirm', 'release', 'refund'));

INSERT INTO i18n_message (key, locale, template) VALUES
    ('RECHARGE_ORDER_NOT_FOUND', 'zh', '充值订单不存在。'),
    ('RECHARGE_ORDER_NOT_FOUND', 'en', 'Recharge order not found.'),
    ('RECHARGE_PROVIDER_ERROR', 'zh', '支付渠道暂时不可用，请稍后重试。'),
    ('RECHARGE_PROVIDER_ERROR', 'en', 'Payment provider is temporarily unavailable. Please try again later.'),
    ('RECHARGE_NOT_REFUNDABLE', 'zh', '该订单无法退款或余额不足以扣回。'),
    ('RECHARGE_NOT_REFUNDABLE', 'en', 'This order cannot be refunded or balance is insufficient to deduct.')
ON CONFLICT (key, locale) DO NOTHING;
