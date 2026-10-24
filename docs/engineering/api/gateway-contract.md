# 内部网关合同：submit / query / result / cancel

以下是 stage-1 内部 DTO 语义。JSON 字段名一律 **snake_case**。

这是 flora 自己的合同，**不是** Anytoken 协议，也不是 OpenAI 兼容表面。实现时可加信封（`data` / `error`），但下面的业务字段不能被 Anytoken 字段名替换。

字段级由 [WP2 brief](../feature-briefs/2026-10-24-wp2-phase1-schema.md) 锁定。

## 公共信封（建议）

```json
{
  "ok": true,
  "data": {},
  "error_code": null,
  "message": null
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ok` | boolean | 是 | 业务是否成功 |
| `data` | object | 成功时是 | 业务载荷 |
| `error_code` | string \| null | 失败时是 | 稳定大写蛇形码，见 [data-model/errors.md](../data-model/errors.md) |
| `message` | string \| null | 失败时是 | 当前 locale 的友好短句，来自 `i18n_message` |

失败时 `ok=false`。**不要把上游字符串填进 `message`。**

## Submit

**用途**：创建一个可执行 job，并在同一事务冻结与写 outbox。

请求：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `idempotency_key` | string | 是 | 同一用户重复提交返回同一 job |
| `capability` | string | 是 | 如 `image.generate` |
| `model` | string | 是 | flora 内部模型标识 |
| `params` | object | 是 | 已物化参数（prompt、尺寸、引用的对象键等）。不要传整张画布文档 |
| `client_job_id` | string | 否 | 若提供，仅作客户端追踪，权威 id 仍由服务端返回 |

响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `job_id` | string (uuid) | 权威 id，也是以后 MQ 的 body |
| `status` | string | 状态机枚举；通常 `queued`；**冻失败则无 job**，返 `WALLET_INSUFFICIENT`（WP6 已写死） |
| `hold_id` | string (uuid) | 若已冻结；WP6 起正常提交恒非空 |
| `error_code` / `message` | string | 仅失败 |

**副作用**：余额不足 → 不入队。同一 `idempotency_key` + 同一请求哈希 → 不二次冻结；同一 key 不同 body → `IDEMPOTENCY_CONFLICT`。

### 冻失败（WP6 已写死）

余额不足（`available < cost`）时 **不创建 job、不写 `wallet_hold`、不写 `wallet_ledger`、不写 `outbox_message`、不写 `idempotency_record`**：

```json
{
  "ok": false,
  "data": null,
  "error_code": "WALLET_INSUFFICIENT",
  "message": "账户余额不足，请先充值。"
}
```

HTTP 状态码仍是 200。充值后用户可用 **同一个 `idempotency_key`** 重新提交。

## Wallet

**用途**：查当前登录用户的钱包余额快照。需登录（`AuthPathPolicy` 覆盖 `/api/v1/**`）。

请求：`GET /api/v1/wallet`，无参数。

响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` | string (uuid) | 钱包主人 |
| `available` | integer | 可用 credit（BIGINT，不出现浮点） |
| `frozen` | integer | 已冻未确认 credit |
| `updated_at` | string (ISO 8601) | 最后一次余额变动时间 |

```json
{
  "ok": true,
  "data": {
    "user_id": "90d6bf80-1234-4567-89ab-cdef01234567",
    "available": 90,
    "frozen": 10,
    "updated_at": "2026-10-24T04:30:00Z"
  },
  "error_code": null,
  "message": null
}
```

用户还没有钱包行时惰性建一行 `available=0, frozen=0` 后返回。**本 WP 不提供 C 端自助充值接口**
（没有 `POST /api/v1/wallet/topup`）：充值走运维 SQL 或未来的 Admin 加额接口。


## Query

**用途**：读状态，给画布轮询或刷新。不触发生成。

请求：`job_id`（string / uuid，路径或 query）。

响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `job_id` | string (uuid) | 回显 |
| `status` | string | 状态机枚举，见 [data-model/jobs.md](../data-model/jobs.md) |
| `attempt` | integer | 已开始的 attempt 数 |
| `max_attempts` | integer | 上限，便于 UI 显示重试 |
| `error_code` / `message` | string | 失败时的友好信息 |
| `updated_at` | string (ISO 8601) | 避免无变化空转时可配合条件请求 |

**不包含**：`raw_body`、Anytoken 请求、供应商渠道名、堆栈。

## Result

**用途**：成功后取输出引用；未成功时与 query 类似，仍不带原文。

请求：`job_id`（string / uuid）。

响应：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `job_id` | string (uuid) | 回显 |
| `status` | string | 必须先看状态，再读 outputs |
| `outputs` | array | 成功时：`[{ "type": "image", "object_key": "...", "url": "短期读地址" }]` |
| `error_code` / `message` | string | 非成功 |

`outputs[]` 元素：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `type` | string | 是 | 如 `image` |
| `object_key` | string | 是 | TOS 对象键 |
| `url` | string | 否 | api 用 TOS 签名的短期读地址 |

`url` 由 api 用 TOS 签名，短 TTL。worker 只写 `object_key`。

## Cancel

请求：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `job_id` | string (uuid) | 是 | 要取消的任务 |

响应：当前状态。已终态：幂等返回当前状态。运行中：置 `cancel_requested_at`；最终 `cancelled` 并 release。用户得到 `JOB_CANCELLED` 或成功取消的友好句，而不是上游 cancel 原文。

## 规范化规则

1. **flora 内部字段名 ≠ Anytoken 字段名。** 映射只发生在 gateway 适配层（Worker 侧），C 端合同永远用 flora 自己的名字。
2. **`message` 只来自 `i18n_message` 模板**，禁止填上游字符串，禁止 `e.getMessage()` 直出。
3. **`error_code` 只能取自注册表**（见 [data-model/errors.md](../data-model/errors.md)）；未识别上游落到泛化码，原文进 `provider_task`。
4. **时间一律 ISO 8601 字符串**（服务端存 `TIMESTAMPTZ`）；**金额一律 BIGINT 整数 credit**，不出现浮点。
5. **枚举值用小写蛇形**（`image.generate`、`queued`、`succeeded`），与 DB 的 `TEXT` + `CHECK` 取值一致。
6. **Worker → Anytoken 的 HTTP 不是本合同的一部分**，不在这份文档里定义。
7. 空值与缺省：可空字段缺省时省略或给 `null`，由实现 WP 统一；客户端必须容忍两者。

## Worker 内部（不是 HTTP C 端）

队列消息：

```json
{ "job_id": "<uuid>" }
```

可加传输层 `event_id`。**禁止**塞 `params` / 图片 / 钱包额。

Worker 调 Anytoken 的请求 / 响应 **不** 定义为本合同的一部分。映射发生在基础设施层：上游失败 → `error_code` + `provider_task.raw_body`。

## 画布侧怎么用

1. 用户点生成 → web 收集节点上已有参数 → `submit`。
2. 画布用 `job_id` 轮询 `query`（间隔在 WP7 brief 确认，禁止实现者随手写死长轮询风暴）。
3. `succeeded` → `result` 拉 URL → 写入节点展示。
4. 失败 → 展示 `message` + 可复制的 `error_code`（给客服），不展示 raw。
