# 内部网关合同：submit / query / result / cancel

以下是 stage-1 内部 DTO 语义。JSON 字段名一律 **snake_case**。

这是 pilipala 自己的合同，**不是** Anytoken 协议，也不是 OpenAI 兼容表面。实现时可加信封（`data` / `error`），但下面的业务字段不能被 Anytoken 字段名替换。

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
| `model` | string | 是 | pilipala 内部模型标识 |
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

### 视频任务（WP12）

**用途**：提交一个托管视频生成任务。能力名 `video.generate`，与 `image.generate` 走同一套
冻结 / 队列 / 结算链路，只是 worker 侧换成 Anytoken 的视频协议。

请求（`POST /api/v1/jobs/submit`）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `idempotency_key` | string | 是 | 与生图同一套幂等语义 |
| `capability` | string | 是 | 固定 `video.generate` |
| `model` | string | 是 | `seedance2` / `seedance2.5` / `seedance-fast` / `seedance-mini`（`model_display_config` 里 `capability = 'video.generate'` 的行） |
| `params` | object | 是 | 已物化参数，见下表 |

`params` 字段（**只允许已物化参数**：严禁塞画布文档、Base64 图片或浏览器里的 API Key）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `prompt` | string | 是 | 提示词 |
| `size` | string | 否 | 画面比例串（如 `16:9`）或像素串（如 `1280x720`）；worker 两种都认 |
| `seconds` | string | 否 | 时长（秒） |
| `duration` | number | 否 | 时长（秒），与 `seconds` 等价，`seconds` 缺失时用它 |
| `resolution` | string | 否 | 如 `720p` / `1080p` |
| `aspect_ratio` | string | 否 | 显式比例，优先于 `size` 的推导 |
| `reference_images` | array | 否 | 参考图对象键列表 |
| `reference_videos` | array | 否 | 参考视频对象键列表 |

示例：

```json
{
  "ok": true,
  "data": {
    "job_id": "3f2b9a10-7c4d-4a6e-9b21-0d5e8c1a4f77",
    "status": "queued",
    "hold_id": "90d6bf80-1234-4567-89ab-cdef01234567",
    "error_code": null,
    "message": null
  },
  "error_code": null,
  "message": null
}
```

失败时的 `error_code` 见 [data-model/errors.md](../data-model/errors.md)：视频专有码有
`GATEWAY_TIMEOUT`（轮询超限，可重试）、`GATEWAY_INVALID_RESPONSE`（上游没给任务 id，不可重试）、
`UPSTREAM_VIDEO_FAILED`（上游失败终态，不可重试）。

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

用户还没有钱包行时惰性建一行 `available=0, frozen=0` 后返回。C 端自助充值走 **WP16** `/api/v1/billing/recharge/**`（z-pay 支付宝 mapi qrcode），
不再提供 `POST /api/v1/wallet/topup`。运维 SQL 与 Admin 手工加额仍可用。

### 充值（WP16，z-pay 支付宝）

环境变量（**禁止**把真实商户密钥写入仓库）：`PILIPALA_PUBLIC_BASE_URL`（无尾 `/`，经 env 注入、代码不写死）、`PILIPALA_ZPAY_PID`、`PILIPALA_ZPAY_PKEY`、可选 `PILIPALA_ZPAY_CID`（默认 `12540`）、`PILIPALA_ZPAY_API_BASE`（默认 `https://api.z-pay.cn`）。缺配置时 **创建订单** 返回 `VALIDATION_ERROR`。

**生产（已确认）**：`PILIPALA_PUBLIC_BASE_URL=https://pilipala.art`。

| 方法 | 路径 | 鉴权 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/v1/billing/recharge/packages` | 登录 | 可购套餐列表 |
| POST | `/api/v1/billing/recharge/orders` | 登录 | body：`package_id`，可选 `device=mobile`；返回 `out_trade_no`、`pay_url`、`qrcode_url` 等 |
| GET | `/api/v1/billing/recharge/orders/{outTradeNo}` | 登录、本人 | `pending_pay` 时可触发一次查单补单（限流） |
| GET/POST | `/api/v1/billing/recharge/webhook/zpay` | 匿名 | 验签成功入账后 HTTP 200，body **恰好** `success`（纯文本）；失败 400 |

`notify_url` = `{PILIPALA_PUBLIC_BASE_URL}/api/v1/billing/recharge/webhook/zpay`；`return_url` = `{PILIPALA_PUBLIC_BASE_URL}/billing/recharge/result`（前端页，不入账）。生产示例：notify `https://pilipala.art/api/v1/billing/recharge/webhook/zpay`，return `https://pilipala.art/billing/recharge/result`。

Admin：`GET /api/v1/admin/billing/recharge-orders`；`POST /api/v1/admin/billing/recharge-orders/{id}/refund`（全额退，`paid` 且余额足够扣回）。

错误码：`RECHARGE_ORDER_NOT_FOUND`、`RECHARGE_PROVIDER_ERROR`、`RECHARGE_NOT_REFUNDABLE`（见 [errors.md](../data-model/errors.md)）。


## Projects（`/api/v1/projects`）

**用途**：C 端账号级画布项目云端持久化。需登录（`AuthPathPolicy` 覆盖 `/api/v1/**`）。
`user_id` 一律取会话，**不接受请求体或 query 里的 `user_id`**。

### `POST /api/v1/projects`

请求：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 是 | 1..128 字符 |
| `canvas_data` | object | 否 | 画布快照；必须是 JSON Object，序列化后 ≤ 2,000,000 字符 |

响应：项目详情（见下）。

### `GET /api/v1/projects`

响应：当前用户的项目摘要数组，**不含 `canvas_data`**，按 `updated_at DESC` 排序。

### `GET /api/v1/projects/{projectId}`

响应：项目详情（含 `canvas_data`）。

### `PUT /api/v1/projects/{projectId}`

请求：`name` 与 / 或 `canvas_data` 均可选（部分更新，未提供的字段保持原值）。

响应：项目详情，`version` 每次成功更新 **+1**。

### `DELETE /api/v1/projects/{projectId}`

响应：`{"id": "...", "deleted": true}`。

### 项目视图字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string (uuid) | 云端项目 id；浏览器本地项目 id 是 nanoid，两者不可混用 |
| `name` | string | 项目名 |
| `version` | integer | 每次成功更新 +1，前端用来显示「已同步 v{version}」 |
| `canvas_data` | object | 仅详情返回：`nodes` / `connections` / `chatSessions` / `activeChatId` / `backgroundMode` / `showImageInfo` / `viewport` |
| `created_at` / `updated_at` | string (ISO 8601) | |

失败：项目不存在或属于他人 → `ok=false, error_code="PROJECT_NOT_FOUND"`，**不泄露他人资源是否存在**。

## Assets（`/api/v1/assets`）

**用途**：当前用户的云端素材列表与删除。需登录。

### `GET /api/v1/assets?limit=N`

`limit` 缺省 100，越界收敛到 1..100。

调用时会把当前用户 **已 `succeeded`** 任务的输出对象键幂等同步进 `user_asset`（重复调用不报错、不产生重复行），并为每条记录 **现签** 短期 TOS 读地址。

### `DELETE /api/v1/assets/{assetId}`

响应：`{"id": "...", "deleted": true}`。失败：`error_code="ASSET_NOT_FOUND"`。

### 素材视图字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string (uuid) | 素材 id |
| `project_id` | string (uuid) \| null | 关联的云端画布项目 |
| `job_id` | string (uuid) \| null | 来源任务 |
| `source` | string | `generated` 或 `uploaded` |
| `object_key` | string | TOS 对象键 |
| `signed_url` | string \| null | 现签的短期读地址；对象键非法时为 null |
| `mime_type` | string | |
| `created_at` | string (ISO 8601) | |

**数据库只存 `object_key`，绝不落预签名 URL 或图片 Base64**（ADR 0003）：每次查询都由 api 调 `TosUrlSigner.signGetUrl(objectKey)` 现签。

## Job History（`/api/v1/jobs/history`）

**用途**：当前用户的生图任务历史，跨设备可见。需登录。

请求：`GET /api/v1/jobs/history?limit=N`（`GET /api/v1/jobs` 等价），按 `created_at DESC` 排序。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `job_id` | string (uuid) | |
| `status` | string | 状态机枚举 |
| `capability` | string | 如 `image.generate` |
| `model` | string | |
| `prompt` | string | 来自任务参数 |
| `cost_credits` | integer | 该任务扣除的 credit |
| `result_urls` | array | 成功任务的对象键现签读地址；失败任务为空数组 |
| `error_code` / `message` | string \| null | 失败时的稳定码与本地化友好短句 |
| `created_at` / `updated_at` | string (ISO 8601) | |
| `finished_at` | string (ISO 8601) \| null | 终态时取最后更新时间，未终态为 null |

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
| `outputs` | array | 成功时：`[{ "type": "image", "object_key": "...", "url": "短期读地址" }]`；视频任务是 `{ "type": "video", "object_key": "...", "url": "短期读地址" }` |
| `error_code` / `message` | string | 非成功 |

`outputs[]` 元素：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `type` | string | 是 | `image` 或 `video`（WP12 起：`job.capability` 以 `video` 开头，或产物对象键以 `.mp4` 结尾时返回 `video`） |
| `object_key` | string | 是 | TOS 对象键 |
| `url` | string | 否 | api 用 TOS 签名的短期读地址 |

`url` 由 api 用火山 TOS V4（`TOS4-HMAC-SHA256`）签发短期预签名 GET 地址（默认 TTL 900 秒，**WP8 已落地**）；worker 只写 `object_key`。

签名地址是 **现签** 的：不落库、不进 MQ 消息、不进 `job` 行（ADR 0003）。任务不是 `succeeded` 时 `outputs` 恒为空数组，不给未完成或失败的任务签发读地址。

## Cancel

请求：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `job_id` | string (uuid) | 是 | 要取消的任务 |

响应：当前状态。已终态：幂等返回当前状态。运行中：置 `cancel_requested_at`；最终 `cancelled` 并 release。用户得到 `JOB_CANCELLED` 或成功取消的友好句，而不是上游 cancel 原文。

## Models（`/api/v1/models`）

**用途**：C 端读取当前上架的托管模型展示项。需登录（`AuthPathPolicy` 覆盖 `/api/v1/**`）。

请求：`GET /api/v1/models`，无参数。

响应：`model_display_config` 里 `enabled = true` 的行，按 `sort_order ASC, model ASC` 排序。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string (uuid) | |
| `model` | string | pilipala 内部模型标识 |
| `capability` | string | 如 `image.generate` |
| `display_name` | string | 前端展示名 |
| `enabled` | boolean | 是否上架；本接口只返回上架的，恒为 `true` |
| `sort_order` | integer | 排序位 |
| `updated_at` | string (ISO 8601) | |

这张表只管展示与上下架，**不含任何供应商 API Key**，也**不是**额度账本（ADR 0001）。

## Admin（`/api/v1/admin/**`）

**用途**：运维与配置后台（M4）：查用户与钱包、手工加额、查任务与上游留痕、重投死信、在线改文案、模型上下架。**不是**第二个创作台。

### 权限闸门

| 场景 | 结果 |
| --- | --- |
| 未登录 | `ok=false, error_code="UNAUTHORIZED"`（`AuthPathPolicy` 覆盖整个 `/api/v1/**`） |
| 已登录但 `users.role != 'admin'` | `ok=false, error_code="FORBIDDEN"`，HTTP 仍为 200 |
| 管理员 | 正常返回 `data` |

目标用户只能来自 **路径参数或查询参数**；接口不接受请求体里的 `user_id` 作为身份来源。

### `GET /api/v1/admin/users?q=&limit=50`

`q` 按 `username` 模糊匹配（`ILIKE`），为空则列全部；`limit` 缺省 50，收敛到 1..200；按 `created_at DESC` 排序。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` | string (uuid) | |
| `username` | string | |
| `role` | string | `user` \| `admin` |
| `locale` | string \| null | |
| `available` | integer | 来自 `wallet.available`；用户还没有 `wallet` 行时为 0 |
| `frozen` | integer | 来自 `wallet.frozen`；同上为 0 |
| `created_at` / `updated_at` | string (ISO 8601) | |

### `POST /api/v1/admin/wallets/{userId}/topup`

请求体：`{"amount": 100}`。`amount` 必须为正整数；`userId` 对应的用户不存在时返回 `VALIDATION_ERROR`。

响应：钱包快照（字段同 [Wallet](#wallet)）。

**单账本红线（ADR 0001）**：加额 **必须** 走既有 `TopUpWallet.topUp`，
在同一事务里加 `wallet.available` 并写一条 `type='topup'` 的 `wallet_ledger`。
禁止绕过账本直接 `UPDATE wallet`。

### `GET /api/v1/admin/wallets/{userId}/ledger?limit=50`

`limit` 缺省 50，收敛到 1..200；按 `created_at DESC` 排序。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string (uuid) | |
| `user_id` | string (uuid) | |
| `type` | string | `topup` \| `freeze` \| `confirm` \| `release` |
| `amount` | integer | 恒为正整数，方向由 `type` 决定 |
| `hold_id` | string (uuid) \| null | 关联冻结单 |
| `job_id` | string (uuid) \| null | 关联任务 |
| `created_at` | string (ISO 8601) | |

### `GET /api/v1/admin/jobs?status=&user_id=&limit=50`

`status` 过滤 `job.status`，取值必须是合法状态（`created` / `queued` / `running` / `succeeded` / `failed` / `cancelled` / `dead`），非法值返回 `VALIDATION_ERROR`；
`user_id` 按用户过滤；`limit` 缺省 50，收敛到 1..200；按 `created_at DESC` 排序。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `job_id` | string (uuid) | |
| `user_id` | string (uuid) | |
| `username` | string | 关联 `users.username` |
| `status` | string | 状态机枚举 |
| `capability` | string | 如 `image.generate` |
| `model` | string | |
| `prompt` | string | 从 `job.params` 提取；缺失为空串 |
| `attempt` / `max_attempts` | integer | |
| `hold_id` | string (uuid) \| null | |
| `hold_status` | string \| null | 关联 `wallet_hold.status`：`frozen` \| `confirmed` \| `released` |
| `error_code` | string \| null | |
| `created_at` / `updated_at` | string (ISO 8601) | |
| `steps` | array | 该任务的全部 `job_step`，按 `attempt ASC`；元素含 `step_id`、`attempt`、`status`、`error_code`、`provider_task_id`、`started_at`、`ended_at` |
| `provider_tasks` | array | 该任务的全部 `provider_task`，按 `created_at ASC`；元素含 `id`、`job_step_id`、`upstream_request_id`、`raw_status`、`raw_body`、`created_at` |

**安全边界（ADR 0005）**：`raw_status` 与 `raw_body` **只允许**出现在 `/api/v1/admin/**` 的响应里。
C 端 `/api/v1/jobs/**`（`query` / `result` / `history`）严禁包含这两个字段。

### `POST /api/v1/admin/jobs/{jobId}/retry`

把 `dead` 或 `failed` 的任务重新入队。任务不存在 → `JOB_NOT_FOUND`；当前状态不是 `dead` / `failed` → `VALIDATION_ERROR`。

响应：更新后的任务视图（此时 `status = "queued"`）。

重投必须守住的三点：

1. **保留 `attempt` 与历史 `job_step`**（`job_step` 有 `UNIQUE (job_id, attempt)`，且 claim 会做 `attempt + 1`）；`max_attempts` 改成 `GREATEST(max_attempts, attempt) + 1`；`error_code` 与 `cancel_requested_at` 清空。
2. **冻结单已结清就解绑**：原 `wallet_hold.status` 不再是 `frozen` 时，把 `job.hold_id` 置为 `null`，避免二次结算同一张冻结单；仍是 `frozen` 则保留。
3. **同事务写 outbox**：`insertPending(eventId, "job", jobId, {"job_id":"...","event_id":"..."})`，由既有 dispatcher 投递给 worker（消息体只有 `job_id`，ADR 0003）。

### `GET /api/v1/admin/i18n-messages?q=`

按 `key ASC, locale ASC` 排序；`q` 非空时按 `key` 或 `template` 包含该关键字过滤。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string (uuid) | |
| `key` | string | 稳定键，常与 `error_code` 相同 |
| `locale` | string | `zh` \| `en` |
| `template` | string | 短句模板 |
| `updated_at` | string (ISO 8601) | |

### `PUT /api/v1/admin/i18n-messages`

请求体：`{"key": "WALLET_INSUFFICIENT", "locale": "zh", "template": "余额不足，请联系管理员充值。"}`。

`key` 去空白后长度 1..128，`locale` 只能是 `zh` / `en`，`template` 去空白后长度 1..1000；不合法一律 `VALIDATION_ERROR`。

响应：更新后的词条。写入 **即刻生效**：`I18nMessageService` 每次解析都查表，不需要重启 api。

### `GET /api/v1/admin/models`

返回 `model_display_config` 全部行（含已下架的），按 `sort_order ASC, model ASC` 排序。字段同 [Models](#modelsapi-v1models)。

### `PUT /api/v1/admin/models/{model}`

请求体：`{"display_name": "GPT Image 2", "capability": "image.generate", "enabled": false, "sort_order": 10}`。

`display_name` 与 `{model}` 为空时返回 `VALIDATION_ERROR`；`{model}` 不存在则 upsert 插入一行。
`capability` / `enabled` / `sort_order` 不传时沿用已有值。响应：更新后的展示配置。

## 规范化规则

1. **pilipala 内部字段名 ≠ Anytoken 字段名。** 映射只发生在 gateway 适配层（Worker 侧），C 端合同永远用 pilipala 自己的名字。
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

Worker 调网关（Anytoken / NewAPI）的请求 / 响应 **不** 定义为本合同的一部分。映射发生在基础设施层：上游失败 → `error_code` + `provider_task.raw_body`。

### Seedance 视频三步协议（WP12，Anytoken 存量）

> WP21 起，新的视频产品走 NewAPI 网关（见下节）；本节保留 Anytoken 存量路径，不删。

`capability` 以 `video` 开头时，worker 走 Anytoken 的 **视频** 端点（与生图的
`/v1/images/generations` 分开），用 `PILIPALA_GATEWAY_VIDEO_API_KEY` 认证：

| 步 | 请求 | 用途 |
| --- | --- | --- |
| 1 | `POST /v1/videos/generations` | 提交异步视频任务；请求头带 `Idempotency-Key: <jobId>:<attempt>`；body **只含** `model` / `prompt` / `aspect_ratio` / `duration` /（可选）`resolution` /（可选）`reference_images` /（可选）`reference_videos`。响应里取任务 id（`id` → `task_id` → `request_id` → `data.id`）与初始 `status` |
| 2 | `GET /v1/videos/{taskId}` | 轮询状态；成功终态 `completed` / `succeeded` / `success`，失败终态 `failed` / `cancelled` / `error`（大小写不敏感）。轮询上限与间隔由 `pilipala.gateway.anytoken.video-max-poll-attempts` / `video-poll-interval-ms` 控制 |
| 3 | `GET /v1/videos/{taskId}/content` → 回退到直链 | 优先试 `/content` 下载字节；**该端点在实测部署里不存在（404 `video not found`）**，必须回退到轮询响应里的成片直链 |

**上游报文的两条硬约束（实测踩出来的，写下来免得再踩）：**

1. **请求 body 不能带 pilipala 内部字段**（`job_id` / `attempt` / `capability`）。
   实测 Anytoken 看到 `capability` 会把请求**路由到 xAI 上游**并直接返回 400
   （`{"error":{"message":"xAI upstream returned status 400"}}`），Seedance 视频根本起不来。
   任务身份靠 `Idempotency-Key` 头带，权威记录在 `job` / `job_step` / `provider_task` 表里。
2. **成片直链在 `result.url`**，不是顶层 `url` / `video_url`。
   实测轮询响应形状：

   ```json
   {
     "task_id": "4b2c44e0d8e13f0d7ea98474e9806dfb",
     "model": "seedance-2.0-fast",
     "status": "completed",
     "progress": 100,
     "poll_after_ms": 5000,
     "result": { "url": "https://media.xiaoni-apikey.top/file_.../....mp4", "mime_type": "video/mp4" }
   }
   ```

   直链本身可匿名下载。取直链时依次看 `result.url` / `result.video_url` / `url` /
   `video_url` / `data.url` / `data[0].url` / `output.video_url`。

产物对象键固定为 `anytoken/vid/{jobId}/{attempt}.mp4`，以 `video/mp4` 写入火山 TOS；
worker 只写 `object_key`，读地址仍由 api 现签（同 `Result` 章节）。

错误落点：

| 情况 | `error_code` | 可重试 |
| --- | --- | --- |
| 提交 / 轮询返回 429 或 5xx | `PROVIDER_UNAVAILABLE` | 是 |
| 提交 / 轮询返回其他 4xx | `JOB_FAILED` | 否 |
| 上游进入失败终态 | `UPSTREAM_VIDEO_FAILED` | 否 |
| 轮询达到上限仍未进终态 | `GATEWAY_TIMEOUT` | 是 |
| 上游没给任务 id | `GATEWAY_INVALID_RESPONSE` | 否 |

上游原文一律进 `provider_task.raw_status` / `raw_body`（仅 Admin 可见），**不**进 C 端 `message`。

### NewAPI 视频网关（WP21）

视频多渠道走独立 NewAPI 网关（[ADR 0008](../adr/0008-gateway-layer-video-multi-channel.md)），与 Anytoken 存量分开。认证 `Authorization: Bearer ${PILIPALA_NEWAPI_API_KEY}`，base 为 `PILIPALA_NEWAPI_BASE_URL`。

| 步 | 请求 | 说明 |
| --- | --- | --- |
| 1 | `POST /v1/video/generations` | body 至少 `model` / `prompt` / `duration` / `size`；返回 `id` / `task_id` / `status`（实测 `RUNNING`） |
| 2 | `GET /v1/video/generations/{task_id}` | 状态 `RUNNING` → `SUCCEEDED` / `FAILED: <message>`；成片 URL 在 **`object`** |
| 3 | 下载 `object` | 实测 `GET /content` 返回 404；不要依赖 content 端点；URL 可匿名下载，成功后立即落 TOS |

实测（c4 / 4s / 1280x720）：提交 200，约 78s 后 `SUCCEEDED`，`object` 下载 795,903 bytes，`ffprobe` 4.096s。

**硬约束（实测踩出来的）：**

1. `status` 可能是 `FAILED: <message>`，不能只做精确匹配；按前缀 `FAILED` 处理。
2. 非法参数可能返回 400 + 一个已失败的 task（`fail_to_fetch_task`），映射为不可重试的 `JOB_FAILED` / `VALIDATION_ERROR`。
3. 不存在的 task 返回 HTTP 400 `task_not_exist`；不要无限轮询。
4. `seconds` 恒为 0，不能当进度。
5. 成片 URL 会过期（上游说明 3-5h），必须成功后立即下载/TOS。

错误落点：

| 情况 | `error_code` | 可重试 |
| --- | --- | --- |
| 提交 / 查询 429 或 5xx | `PROVIDER_UNAVAILABLE` | 查询原地退避；提交仅在无 task id 时 |
| 提交 / 查询其他 4xx | `JOB_FAILED` | 否 |
| `status` 前缀 `FAILED` | `UPSTREAM_VIDEO_FAILED` | 否（可走产品级 fallback） |
| 查询超过 target 预算 | `GATEWAY_TIMEOUT` | 先按 task id 对账，再决定 |
| `task_not_exist` | `GATEWAY_INVALID_RESPONSE` | 否 |

产物对象键建议 `newapi/vid/{jobId}/{attempt}.mp4`，`video/mp4`。渠道级重试/负载均衡在 NewAPI；产品级 fallback 在 pilipala，见 WP21 brief。

## 画布侧怎么用

1. 用户点生成 → web 收集节点上已有参数 → `submit`。
2. 画布用 `job_id` 轮询 `query`（间隔在 WP7 brief 确认，禁止实现者随手写死长轮询风暴）。
3. `succeeded` → `result` 拉 URL → 写入节点展示。
4. 失败 → 展示 `message` + 可复制的 `error_code`（给客服），不展示 raw。
