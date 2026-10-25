# Feature brief：WP16 预付费钱包充值 — z-pay 彩虹易支付（支付宝 qrcode / mapi.php）

- 日期：2026-10-25
- 作者：flora engineering
- 对应 WP / 里程碑：WP16 / M4+（C 端自助充值闭环；接 WP6 钱包 + WP14 MyBatis-Plus）
- 状态：已确认（待实现）
- 建议分支：`feat/wp16-recharge-zpay-alipay`
- 预计 PR 数：1（允许同一 PR 内分 commit：migration → zpay client → create/webhook/reconcile → admin/refund → docs/web；**不要**拆成「只建表、不能入账」的半成品 PR）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax / 云端代理。不要摘要。实现者只改本 brief 点名的范围；验收以「仓内编译测试通过 + 支付宝下单/验签/入账/补单语义正确 + 仓库无商户密钥明文」为准。

---

## 0. 给实施者（Minimax / cloud-agent）的先读指令

动工前**必须**按顺序读完：

1. `AGENTS.md`
2. `docs/engineering/TECH_STACK.md`
3. `docs/engineering/ARCHITECTURE.md`（billing 域；四层分包）
4. `docs/engineering/adr/0001-anytoken-not-newapi-fork.md`（**单账本**）
5. `docs/engineering/adr/0004-thin-worker-no-god-classes.md`（worker **零**钱包/充值）
6. `docs/engineering/adr/0005-friendly-errors-vs-raw-upstream.md`
7. `docs/engineering/adr/0006-mybatis-plus-persistence.md`（Mapper + 显式条件 SQL）
8. `docs/engineering/data-model/billing.md`（**照抄源**；本 WP 会扩展 `recharge_order` 与 ledger 追溯列）
9. `docs/engineering/data-model/errors.md`
10. `docs/engineering/api/gateway-contract.md`
11. `docs/engineering/feature-briefs/2026-10-24-wp6-prepaid-wallet.md`（`topup` / freeze 语义）
12. `docs/engineering/feature-briefs/2026-10-25-wp14-mybatis-plus-code-migration.md`（infra 现状）
13. `api/src/main/java/com/flora/api/billing/**`（尤其 `TopUpWallet`、`WalletService`、`WalletLedgerType`、`AuthPathPolicy`）
14. `api/src/main/resources/db/migration/V4__create_wallet_domain.sql` 与最新迁移编号（本 WP 用 **V9**）

**本 WP 的硬约束：**

- **域**：全部在 **`billing`**（及 admin 只读/退款入口调用 billing 用例）。**禁止**新建独立 `payment` 域包；**禁止**在 `auth` 实现支付。
- **入账**：支付成功后**必须**走现有 `TopUpWallet` / `WalletService.credit`（或等价、同一事务路径），写下 `wallet_ledger.type = 'topup'`，并关联 `recharge_order_id`。**禁止**只 `UPDATE wallet.available` 不写流水。
- **渠道**：仅 **支付宝**（`type=alipay`，`cid` 来自配置默认 `12540`）。**禁止**实现 `wxpay` / 微信通道。
- **下单**：只走 **`mapi.php` + qrcode 模式**。**禁止**走 `submit.php`。
- **回调 URL**：每笔订单自带 flora 的 `notify_url` / `return_url`，由 **`FLORA_PUBLIC_BASE_URL`** 拼出。**严禁**照抄 oioio：
  - ~~`https://oioio.chat/api/v1/payment/webhook/easypay`~~
  - ~~`https://oioio.chat/payment/result`~~
- **密钥**：**禁止**把真实 `pid` / `pkey` 写入任何入库文件、测试 fixture、截图、PR 描述或注释。只用 env 占位符（见 §7）。本地联调密钥放部署环境 / 本机 env / vault，不进 git。
- **与 oioio 共商户**：`out_trade_no` **全局唯一**，前缀**不得**用 oioio 的 `sub2_`（见 §7 锁定 `flr_`）。
- **持久化**：MyBatis-Plus 3.5.17；包结构 `com.flora.api.billing.{web,application,domain,infra}`；入账/状态迁移用显式条件 `@Update`（ADR 0006）。
- **worker**：**零改动**（不依赖 z-pay、不碰 `recharge_order`）。
- **不做**：analytics Phase 2 ingest；改 job freeze/confirm/release 语义；微信；把商户密钥硬编码。

---

## 1. 背景

WP6 已落地预付费钱包（`wallet` / `wallet_hold` / `wallet_ledger`），入账类型已有 `topup`，但 C 端**没有**支付渠道：运维靠 Admin 手工加额或 SQL。`billing.md` 已预告：未来充值订单与第三方支付适配属 **billing** 域。

产品选定 **彩虹易支付协议**、服务商 **z-pay**（`https://api.z-pay.cn`），与 oioio **共用同一商户号**，但每笔订单必须传 **本系统** 的 notify/return，且订单号前缀与 oioio 隔离，否则付款通知会打到错误系统或撞号。

WP14 已将 billing infra 迁到 MyBatis-Plus；本 WP 在同一分包内新增充值订单与 z-pay 适配。

---

## 2. 目标（可验证）

1. Flyway **`V9__create_recharge_order.sql`**：新建 `recharge_order`；`wallet_ledger` 增加可空 `recharge_order_id`（逻辑关联 + 索引；**不要** `ON DELETE CASCADE`）。
2. 配置齐全且可启动：`FLORA_PUBLIC_BASE_URL`、`FLORA_ZPAY_PID`、`FLORA_ZPAY_PKEY`、`FLORA_ZPAY_CID`（默认 12540）；缺必填配置时**创建订单应失败**（清晰 `VALIDATION_ERROR` / 启动期校验二选一，brief 锁定见 §7）。
3. 已登录用户可按**套餐**创建支付宝充值单，拿到**绝对 URL** 的 `qrcode` / `payurl`（手机优先 `payurl2`）。
4. z-pay 异步通知：验签 → 字段白名单 → `TRADE_SUCCESS` → 金额一致 → **幂等入账一次** → HTTP 200 正文恰好 `success`。
5. 主动查单补单：notify 丢失时，定时对账 + 客户端查询接口可把订单收敛到 `paid` 并入账；**不**只靠 return 页。
6. Admin：列表查询充值单；退款：实现 z-pay refund 客户端 + Admin 触发路径（扣回 credits + ledger `refund` + 订单 `refunded`）。
7. 文档：`billing.md` / `gateway-contract.md` / `errors.md` / `DEVELOPMENT.md` / `feature-briefs/README.md` / `local-dev.md` 与本 brief 入仓一致。
8. `mvn -pl api -am test`（及仓内惯用命令）通过；**仓库全文检索无真实 pkey/pid 样例**（占位符除外）。

---

## 3. 范围（允许新增 / 修改）

| 路径 | 动作 |
| --- | --- |
| `docs/engineering/feature-briefs/2026-10-25-wp16-recharge-zpay-alipay.md` | **新增**（本文件全文） |
| `docs/engineering/feature-briefs/README.md` | 索引 + WP16 |
| `docs/engineering/DEVELOPMENT.md` | WP16 小节 + 依赖图延伸 |
| `docs/engineering/data-model/billing.md` | 增加 `recharge_order`；ledger 追溯列；充值小节改为「WP16 已落地渠道」 |
| `docs/engineering/data-model/README.md` | 索引说明 |
| `docs/engineering/data-model/errors.md` | 登记本 WP 新错误码（见 §8） |
| `docs/engineering/api/gateway-contract.md` | 充值 API + webhook 行为 |
| `docs/engineering/runbooks/local-dev.md` | env 占位、curl 验收（**无真实密钥**） |
| `api/src/main/resources/db/migration/V9__create_recharge_order.sql` | **新增** |
| `api/src/main/resources/application.yml` | `flora.public-base-url`、`flora.billing.zpay.*`、`flora.billing.recharge.*` |
| `.env.example`（若仓内已有惯例文件） | 只加占位符键名 |
| `api/src/main/java/com/flora/api/billing/**` | 充值用例、状态机、Zpay 客户端、Mapper、Controller、对账调度 |
| `api/src/main/java/com/flora/api/auth/infra/AuthPathPolicy.java` | **放行** webhook 路径（见 §7） |
| `api/src/main/java/com/flora/api/common/ErrorCodes.java` + i18n 文案路径 | 新码 |
| `api/src/main/java/com/flora/api/admin/**` | 列表 + 退款入口（调用 billing 用例） |
| `api/src/test/java/com/flora/api/billing/**` | 签名、验签、白名单、幂等入账、金额校验、查单解析单测（**mock HTTP**，不打真 z-pay） |
| `web/`（最小） | 结果页路由 `/billing/recharge/result` +（可选）充值入口调 create API；不要求完整运营后台 UI |

---

## 4. 非范围（本 PR 明确不做）

- **wxpay** / 微信 cid / `type=wxpay`。
- 改 job 的 freeze / confirm / release 语义或 SQL。
- analytics Phase 2 ingest / `analytics_event` 表。
- 照抄 oioio notify/return URL。
- 提交真实 `FLORA_ZPAY_PID` / `FLORA_ZPAY_PKEY` 值。
- 新建全局 `payment` 域或把充值塞进 `auth`。
- Stripe / 微信官方直连 / 苹果 IAP。
- 套餐运营中台、优惠券、活动赠额引擎（套餐仅配置列表）。
- 改 `worker/`。
- 回改 WP0–WP15 历史 brief 正文（只允许索引/DEVELOPMENT 指向 WP16）。
- 用 `submit.php` 或浏览器跳转收银台替代 mapi qrcode（本 WP 锁定 mapi）。

---

## 5. 本 WP 拍板（已决定，禁止再猜）

| 项 | 锁定值 |
| --- | --- |
| 分支 | `feat/wp16-recharge-zpay-alipay` |
| Flyway | `V9__create_recharge_order.sql`（**严禁**改 V1–V8） |
| 表名 | `recharge_order` |
| 支付平台 | 彩虹易支付协议；服务商 z-pay；`apiBase=https://api.z-pay.cn`（可配置，默认该值） |
| 支付方式 | **仅** `type=alipay`；`cid` 默认 `12540`（`FLORA_ZPAY_CID` 可覆盖） |
| 下单入口 | `POST {apiBase}/mapi.php`，`Content-Type: application/x-www-form-urlencoded`；**禁止** `submit.php` |
| 查单 | 优先 `GET {apiBase}/api.php?act=order&pid=...&key=PKEY&out_trade_no=...`；GET 无合法 JSON 再 **POST** 同参；查单用 **`key=pkey`**，**不**走 MD5 签名 |
| 退款 | `POST {apiBase}/api.php?act=refund`，表单 `pid`、`key=pkey`、`money`，加 `out_trade_no` 或 `trade_no`；**不**走 MD5；`code=1` 或 `"1"` 成功；先 `out_trade_no`，上游称不存在再换 `trade_no` |
| `out_trade_no` 格式 | **`flr_` + `yyyyMMdd`（UTC+8 日历日）+ 10 位 `[A-Za-z0-9]` 随机**。例：`flr_20261025Ab12Cd34Ef`。总长固定可校验；**禁止** `sub2_` 前缀 |
| `notify_url` | `{FLORA_PUBLIC_BASE_URL}/api/v1/billing/recharge/webhook/zpay` |
| `return_url` | `{FLORA_PUBLIC_BASE_URL}/billing/recharge/result`（前端页，不在 `/api/v1`） |
| `FLORA_PUBLIC_BASE_URL` | **必填**（无尾斜杠）。**禁止**实现者臆造生产域名；未配置则拒绝创建订单 |
| 签名（下单 + 通知验签） | 去掉 `sign`、`sign_type`、空值 → 参数名 ASCII 升序 → `k=v&k=v` → **末尾直接拼接 pkey（pkey 前不加 `&`）** → MD5 → **小写** hex；请求带 `sign` + `sign_type=MD5` |
| 通知字段白名单 | **只接受**：`pid, trade_no, out_trade_no, type, name, money, trade_status, param, sign, sign_type`。**多任一字段 → 拒绝**（不入账、不回 `success`） |
| 入账条件 | `trade_status=TRADE_SUCCESS` **且** 金额与本地订单一致 **且** 订单存在 **且** 尚未入账 |
| 成功应答 | HTTP **200**，body **恰好** `success`（无 JSON、无多余空白策略：trim 后等于 `success`；推荐直接写纯文本 `success`） |
| 钱包单位 | 继续 **BIGINT flora credit**；与 WP6 一致 |
| 人民币存储 | 订单存 **`money_fen` BIGINT**（人民币**分**，正整数）；调 z-pay 时用 `BigDecimal.valueOf(money_fen, 2).toPlainString()` 得到 `money` 字符串（如 `10.00`）。**禁止** `double`/`float` 算钱 |
| 通知金额校验 | 将回调 `money` 解析为分（`BigDecimal` 严格 2 位或可规范化后 ×100）与 `recharge_order.money_fen` **相等**；否则拒绝 |
| 套餐（C 端只选 package_id） | 配置白名单，**禁止**客户端自报任意金额/credits。默认三档（可用 env/yaml 覆盖，但实现须支持列表）：见下表 |
| 入账 ledger | `type='topup'`；写入 `recharge_order_id`；**复用** `TopUpWallet` 能力（扩展方法签名以带上 orderId，或 credit 后回填） |
| 退款 ledger | 扩展 CHECK 增加 **`refund`**：`available -= amount`（amount>0）；订单 → `refunded` |
| 幂等入账 | `UPDATE recharge_order SET status='paid', ... WHERE out_trade_no=? AND status IN ('pending_pay') RETURNING *`（或等价条件）行数=1 才调用 topup；已 `paid` 重放 → 直接成功应答不二次加款 |
| 鉴权 | create / get / list-mine：**需登录**；webhook：**匿名**但验签+白名单；Admin：现有 admin 闸门 |
| AuthPathPolicy | 在放行名单增加 **精确路径** `/api/v1/billing/recharge/webhook/zpay`（与 register/login 同类） |
| 相对 URL | `payurl`/`qrcode`/`payurl2` 若以 `/` 开头 → 前缀 `https://api.z-pay.cn`（或配置的 apiBase）；已是 `https://` / `alipays://` / `weixin://` → 原样。**展示给用户的二维码内容必须是绝对 URL** |
| 手机 | 请求加 `device=mobile`；响应优先 `payurl2`（有则用） |
| worker | **零文件改动** |

### 5.1 默认套餐（可配置覆盖）

| `package_id` | `money_fen` | z-pay `money` | `credits` | 说明 |
| --- | --- | --- | --- | --- |
| `p10` | `1000` | `10.00` | `100` | 10 元 → 100 credit（与默认 job cost 10 对齐粗算） |
| `p50` | `5000` | `50.00` | `550` | 含小额赠送 |
| `p100` | `10000` | `100.00` | `1200` | 含赠送 |

客户端 **只**传 `package_id`；服务端查表填 `money_fen` / `credits` / 商品名。

### 5.2 订单状态机

```
created ──(mapi 成功)──► pending_pay ──(notify/查单成功入账)──► paid
    │                         │
    │                         ├──(超时关闭 / 用户放弃策略)──► closed
    │                         └──(mapi 后明确失败极少)──► failed
    └──(mapi 失败，可选不落库或落 failed)──► failed

paid ──(admin 退款成功)──► refunded
```

- `created`：本地已占 `out_trade_no`，尚未拿到上游成功码（若实现选择「先插后调」，mapi 失败则 `failed`）。
- **推荐写入顺序**：先插 `pending_pay` 前的行（`created`）→ 调 mapi → 成功则更新 `pending_pay` + 填 `provider_trade_no`/`pay_url`/`qrcode_url`；失败则 `failed` + 返回 `msg`。
- `closed`：超过 `flora.billing.recharge.order-ttl-minutes`（默认 **30**）仍未支付，对账任务标记关闭（不入账）。已 `paid` 不可 closed。

### 5.3 为何 notify 挂在 `/api/v1/billing/...`？

- 资金与订单真相在 **billing**；路径表达域边界，避免再造 `/payment` 域。
- 与现有 `/api/v1/wallet` 同前缀，网关/鉴权策略集中。
- webhook 为少数匿名例外，显式写入 `AuthPathPolicy`，避免「整个 billing 匿名」。

### 5.4 为何 return 不入账？

浏览器跳转可伪造/丢失；**只**以验签通知 + 服务端查单补单入账。return 页只展示状态并 **轮询** `GET /api/v1/billing/recharge/orders/{outTradeNo}`（需登录、仅本人）。

---

## 6. 数据表字段

### 6.1 新表 `recharge_order`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK DEFAULT `gen_random_uuid()` | |
| `user_id` | UUID | NO | IDX `(user_id, created_at DESC)`；逻辑 FK → `users(id)` | **不要** `ON DELETE CASCADE` |
| `out_trade_no` | TEXT | NO | **UNIQUE** | `flr_` 格式 |
| `provider` | TEXT | NO | DEFAULT `'zpay'` | 预留多渠道 |
| `provider_trade_no` | TEXT | YES | IDX | z-pay `trade_no` |
| `package_id` | TEXT | NO | | 如 `p10` |
| `money_fen` | BIGINT | NO | CHECK `> 0` | 人民币分 |
| `credits` | BIGINT | NO | CHECK `> 0` | 入账 credit |
| `status` | TEXT | NO | CHECK ∈ `created\|pending_pay\|paid\|failed\|closed\|refunded` | |
| `client_ip` | TEXT | NO | | 下单传给 z-pay 的 `clientip` |
| `device` | TEXT | YES | | `mobile` 或 null |
| `pay_url` | TEXT | YES | | 规范化后的绝对/协议 URL |
| `qrcode_url` | TEXT | YES | | 规范化后的**绝对** URL（给二维码） |
| `product_name` | TEXT | NO | | 传给 z-pay 的 `name`，如 `flora credits p10` |
| `ledger_id` | UUID | YES | | 入账成功后的 `wallet_ledger.id` |
| `paid_at` | TIMESTAMPTZ | YES | | |
| `refunded_at` | TIMESTAMPTZ | YES | | |
| `fail_reason` | TEXT | YES | | 上游 msg / 内部原因（勿存密钥） |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT `now()` | |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT `now()` | |

建议索引：

```sql
CREATE UNIQUE INDEX recharge_order_out_trade_no_key ON recharge_order (out_trade_no);
CREATE INDEX recharge_order_user_id_created_at_idx ON recharge_order (user_id, created_at DESC);
CREATE INDEX recharge_order_status_created_at_idx ON recharge_order (status, created_at);
CREATE INDEX recharge_order_provider_trade_no_idx ON recharge_order (provider_trade_no)
  WHERE provider_trade_no IS NOT NULL;
```

物理 FK：本 WP **允许**对 `users(id)` 使用与存量一致的 `REFERENCES`（无 `ON DELETE CASCADE`）；若项目前向策略更倾向纯逻辑 FK，可只建索引不建物理 FK——**二选一写进迁移并与 `billing.md` 一致**。推荐与 V4 wallet 一致：`REFERENCES users (id)` **无 CASCADE**。

### 6.2 变更 `wallet_ledger`

```sql
ALTER TABLE wallet_ledger
  ADD COLUMN recharge_order_id UUID NULL;

-- 逻辑关联；不要 ON DELETE CASCADE
CREATE INDEX wallet_ledger_recharge_order_id_idx
  ON wallet_ledger (recharge_order_id)
  WHERE recharge_order_id IS NOT NULL;

ALTER TABLE wallet_ledger DROP CONSTRAINT IF EXISTS wallet_ledger_type_check;
ALTER TABLE wallet_ledger
  ADD CONSTRAINT wallet_ledger_type_check
  CHECK (type IN ('topup', 'freeze', 'confirm', 'release', 'refund'));
```

（约束名以库中实际名为准；用 Flyway 先查/按 V4 创建时的默认名处理，确保可重复执行策略符合 Flyway 习惯——通常一次性迁移不需 IF EXISTS，按仓内风格书写。）

Java：`WalletLedgerType` 增加 `REFUND`；`topup` 语义不变。

### 6.3 金额符号补充（写入 billing.md）

| type | `available` | `frozen` | 含义 |
| --- | --- | --- | --- |
| `topup` | `+amount` | — | 充值（含本 WP 渠道入账） |
| `refund` | `-amount` | — | 充值退款扣回（须 `available >= amount`） |

---

## 7. 配置与密钥（占位符 only）

```yaml
flora:
  public-base-url: ${FLORA_PUBLIC_BASE_URL:}   # 生产：https://pilipala.art（env）；勿提交商户密钥
  billing:
    zpay:
      api-base: ${FLORA_ZPAY_API_BASE:https://api.z-pay.cn}
      pid: ${FLORA_ZPAY_PID:}
      pkey: ${FLORA_ZPAY_PKEY:}
      cid: ${FLORA_ZPAY_CID:12540}
      # type 固定 alipay，代码写死，不配 wxpay
    recharge:
      order-ttl-minutes: ${FLORA_RECHARGE_ORDER_TTL_MINUTES:30}
      reconcile-poll-ms: ${FLORA_RECHARGE_RECONCILE_POLL_MS:5000}
      reconcile-batch-size: ${FLORA_RECHARGE_RECONCILE_BATCH_SIZE:50}
      packages:
        - id: p10
          money-fen: 1000
          credits: 100
          name: "flora credits 100"
        - id: p50
          money-fen: 5000
          credits: 550
          name: "flora credits 550"
        - id: p100
          money-fen: 10000
          credits: 1200
          name: "flora credits 1200"
```

环境变量键（写入 `.env.example` / runbook，**值为空或占位**）：

| 变量 | 必填 | 说明 |
| --- | --- | --- |
| `FLORA_PUBLIC_BASE_URL` | 是（创建订单时） | 无尾 `/`；**生产** `https://pilipala.art` |
| `FLORA_ZPAY_PID` | 是 | 商户号 |
| `FLORA_ZPAY_PKEY` | 是 | 商户密钥 |
| `FLORA_ZPAY_CID` | 否 | 默认 `12540` |
| `FLORA_ZPAY_API_BASE` | 否 | 默认 `https://api.z-pay.cn` |

**启动策略（锁定）**：应用可以启动；`CreateRechargeOrder` 时若 `public-base-url` / `pid` / `pkey` 为空 → `VALIDATION_ERROR`（或专用 `PAYMENT_CONFIG_ERROR`，见 §8）。单测用假配置，不读真实密钥。

---

## 8. API / UX

公共信封仍遵守 `gateway-contract.md`（HTTP 200 + `ok` 等），**惟 webhook 例外**：成功必须纯文本 `success`。

### 8.1 `POST /api/v1/billing/recharge/orders`（登录）

请求：

```json
{ "package_id": "p10", "device": "mobile" }
```

- `device` 可选；为 `mobile` 时上游加 `device=mobile`，并优先返回 `payurl2`。
- `clientip`：从请求连接/RemoteAddr（经可信代理头的策略与仓内现有一致；若无现成工具则取 `X-Forwarded-For` 首段或 `remoteAddr`），禁止客户端伪造为唯一来源时仍要有服务端兜底 IP。

响应 `data`：

```json
{
  "id": "...",
  "out_trade_no": "flr_20261025Ab12Cd34Ef",
  "status": "pending_pay",
  "money_fen": 1000,
  "credits": 100,
  "pay_url": "https://...",
  "qrcode_url": "https://...",
  "expire_at": "..."
}
```

### 8.2 `GET /api/v1/billing/recharge/orders/{outTradeNo}`（登录，本人）

返回订单状态；若 `pending_pay`，**可触发一次**同步查单补单（限流：同单最短间隔 eg 3s），再返回最新状态。

### 8.3 `GET /api/v1/billing/recharge/packages`（登录）

返回可购套餐列表（id / money_fen / credits / name），供前端展示。

### 8.4 `GET|POST /api/v1/billing/recharge/webhook/zpay`（匿名）

- 聚合 query 或 form 参数为 Map。
- 验签失败 / 额外字段 / 非 `TRADE_SUCCESS` / 金额不符 / 无订单 → **不要** body `success`（可用 400 或 200+非 success；锁定：**HTTP 400** 或 **200 + 非 success 文本**，推荐 **400** 且 body 短错误，便于与「成功必须 success」区分；z-pay 会重试）。
- 成功：入账幂等后 **200 + `success`**。

### 8.5 Admin

- `GET /api/v1/admin/billing/recharge-orders`：分页/按 user/status/out_trade_no 过滤。
- `POST /api/v1/admin/billing/recharge-orders/{id}/refund`：对 `paid` 单全额退（本 WP 不做部分退款）。校验 `available >= credits`；调 z-pay refund；成功则同事务 `refund` ledger + 状态 `refunded`。

### 8.6 前端

- 结果页路径：**`/billing/recharge/result`**（与 `return_url` 一致）。展示「处理中/成功/失败」，用登录态轮询 8.2。
- 充值入口：可在现有钱包 UI 旁增加最小按钮（选套餐 → create → 展示二维码）；若时间不够，API 完成 + 结果页即可，PR 注明「充值入口 UI 最小/后续」。

### 8.7 错误码（先改 errors.md 再加 ErrorCodes）

| code | 何时 |
| --- | --- |
| `VALIDATION_ERROR` | 非法 package、缺配置、非法参数 |
| `RECHARGE_ORDER_NOT_FOUND` | 查单/本人订单不存在 |
| `RECHARGE_PROVIDER_ERROR` | mapi/`code!=1` 或退款上游失败（C 端看友好短句；原文进日志） |
| `RECHARGE_NOT_REFUNDABLE` | 状态不允许退款或余额不足以扣回 |
| `FORBIDDEN` | Admin 非管理员（已有） |

文案 zh/en 写入现有 i18n 路径（WP9 机制）。

---

## 9. 建议包结构（billing 内扩展）

```
com.flora.api.billing
  domain/
    RechargeOrder.java
    RechargeOrderStatus.java
    WalletLedgerType.java          （+ REFUND）
  application/
    CreateRechargeOrder.java
    GetRechargeOrder.java
    ListRechargePackages.java
    HandleZpayNotify.java
    ReconcileRechargeOrder.java    （查单补单单笔）
    RechargeReconcileTick.java     （批扫 pending_pay）
    RefundRechargeOrder.java
    TopUpWallet.java               （扩展：支持 rechargeOrderId）
  infra/
    ZpayProperties.java
    RechargeProperties.java
    ZpayClient.java                （mapi / order query / refund）
    ZpaySign.java                  （纯函数：sign / verify）
    RechargeOrderMapper.java
    MybatisRechargeOrderRepository.java
    RechargeReconcileDispatcher.java
  web/
    RechargeController.java        （C 端）
    ZpayWebhookController.java     （produces TEXT_PLAIN）
```

Admin 控制器放 `com.flora.api.admin.web`，用例可放 admin.application 但**必须**调用 billing 的 `RefundRechargeOrder` / 查询端口。

---

## 10. z-pay 协议细节（实现照抄）

### 10.1 下单 POST `/mapi.php`

表单字段：`pid`, `type=alipay`, `out_trade_no`, `notify_url`, `return_url`, `name`, `money`, `clientip`, `cid`（配置），可选 `device=mobile`，再 `sign`, `sign_type=MD5`。

成功：`code=1`（注意 JSON 数字或字符串）；取 `trade_no`, `payurl`, `qrcode`；移动端有 `payurl2` 则支付跳转优先它。`code!=1` → 失败，读 `msg`。

### 10.2 签名伪代码

```
filtered = params where key not in (sign, sign_type) and value is not null/blank
sort by key ASC
stringA = join "k=v" with "&"
stringSign = stringA + pkey          // 无额外 "&"
sign = md5(stringSign).hex.lower()
```

### 10.3 查单已支付判定

`trade_status=TRADE_SUCCESS` **或** `status=1`（字段可能在根或 `data` 下）。解析时两处都看。

### 10.4 对账调度

`@Scheduled` 扫描 `status='pending_pay' AND created_at < now()-30s`（避免刚下单尖刺），批量查单；已支付走与 notify **同一**入账方法；超时 TTL → `closed`。

---

## 11. 实现步骤（建议 commit 顺序）

1. V9 迁移 + domain 枚举/实体 + billing.md  
2. `ZpaySign` 单测（固定向量：自造 pid/pkey **假值** `test_pid` / `test_pkey`，断言签名）  
3. `ZpayClient` + mock web server / WireMock  
4. `CreateRechargeOrder` + Controller + AuthPathPolicy  
5. `HandleZpayNotify` + 幂等入账  
6. Reconcile tick + GET 订单  
7. Admin list + refund  
8. 前端结果页最小实现  
9. gateway-contract / errors / DEVELOPMENT / README / runbook  

---

## 12. 验收（严格勾选）

- [ ] 分支 `feat/wp16-recharge-zpay-alipay`（或 PR 说明等价）。
- [ ] `V9__create_recharge_order.sql` 存在；**未**改 V1–V8。
- [ ] `out_trade_no` 前缀为 **`flr_`**，格式符合 §5；**无** `sub2_`。
- [ ] notify/return **仅**由 `FLORA_PUBLIC_BASE_URL` 拼接为 §5 路径；代码与文档中**无** `oioio.chat` 支付回调 URL。
- [ ] 仅 alipay + mapi.php；代码路径**无** wxpay/submit.php 实现。
- [ ] 签名算法单测覆盖；通知额外字段被拒；错误金额不入账；重复通知不二次 `topup`。
- [ ] 入账走 `topup` 流水且 `recharge_order_id` 有值；`available` 增加等于 `credits`。
- [ ] 查单补单与 notify 共用入账；return 页**不**直接加款。
- [ ] webhook 成功应答 body 为确切 `success`。
- [ ] AuthPathPolicy 放行 webhook；其它 billing 充值 API 需登录。
- [ ] Admin 可列表；退款路径存在且成功时 ledger `refund` + 状态 `refunded`（或 PR 若砍退款须在描述声明——**本 brief 默认要求做**）。
- [ ] worker **无**改动。
- [ ] 仓库 / PR / 测试固件中**无**真实商户 pkey/pid；仅占位符与假测试密钥。
- [ ] `mvn -pl api -am test`（或文档等价）通过。
- [ ] 本 brief 入仓；`DEVELOPMENT.md` / `feature-briefs/README.md` / `billing.md` / `gateway-contract.md` / `errors.md` 已更新。
- [ ] PR 描述引用本 brief，列出勾选，写明「未做：wxpay、analytics ingest、改 freeze 语义」。

---

## 13. 非目标（阶段稳定）

- 不 fork NewAPI；不建第二套用户额度（ADR 0001）。
- Worker 不碰充值/钱包（ADR 0004）。
- 不以 CASCADE 删资金流水/订单。
- 不把支付做成跨域上帝模块。

---

## 14. 下一步（Out of scope）

- 微信通道（若未来有 cid，另 brief）。
- 部分退款、对账报表 UI、发票。
- analytics 充值成功事件埋点（可与 WP15 字典对齐，另 PR）。
- 多支付服务商路由。

---

## 15. 给 Minimax / cloud-agent 的额外约束

- 遵守 `docs/engineering/` 全部 Accepted ADR（尤其 0001 / 0004 / 0005 / 0006）。
- **密钥只用占位符**；单测用 `test_pid` / `test_pkey` 之类假值。
- 不改无关文件；一次 PR 专注 WP16。
- 开 PR，标题建议：`feat(wp16): z-pay alipay recharge orders for prepaid wallet`。
- 实现必须遵守本 brief「拍板」表；协议细节以 §10 为准，不要凭记忆简化验签或白名单。

---

## 16. 开放问题（不阻塞编码）

| 问题 | 处理 |
| --- | --- |
| 生产 `FLORA_PUBLIC_BASE_URL` | **已确认** `https://pilipala.art`（仅经 env / 部署配置；Java 仍读 `flora.public-base-url`，单测用 localhost 假值）。 |
| 套餐 credits 是否要改促销数字 | 默认表已锁定；运营改 yaml 即可，不必改代码。 |
| 与 oioio 共商户的对账责任 | 靠 `flr_` 前缀隔离订单号；跨系统财务对账不在本 WP。 |
