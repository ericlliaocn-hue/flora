# Job 概念模型

字段名为概念名，不是最终 SQL。类型与约束在 WP3 brief 定。

## 状态机

```
created → queued → running → succeeded
                ↘ failed ──(attempt < max_attempts)→ queued
                ↘ cancelled
                ↘ dead
```

| 状态 | 含义 | 谁写入 |
| --- | --- | --- |
| `created` | 行已插入，尚未入队 | api |
| `queued` | 可被 worker 领取（含重试回来） | api（首次）/ 状态迁移 |
| `running` | 某次 attempt 进行中 | worker |
| `succeeded` | 终态成功，应 confirm 冻结 | 状态迁移（worker 汇报后） |
| `failed` | 本次 attempt 失败；若还可重试则回到 `queued` | worker 汇报后 |
| `cancelled` | 终态取消，应 release | api 或 worker 观察到取消意图后 |
| `dead` | 不可再执行，已进或应进 DLQ，应 release | 状态迁移 |

`failed` 是 **中间可见状态**，不是必须停留的终态。对用户查询：正在重试可展示「失败重试中」；不再重试则很快进入 `dead` 或保持失败终态——WP3 brief 必须二选一并写进验收。默认倾向：不可重试或 attempt 用尽 → `dead`。

## `job`

| 概念字段 | 说明 |
| --- | --- |
| `id` | 主键，也是 MQ 消息体 |
| `user_id` | 提交者 |
| `status` | 上表枚举 |
| `capability` | 如 `image.generate` |
| `model` | flora 内部模型名（映射到 Anytoken 在基础设施层做） |
| `params_ref` | 参数本体（JSONB 或附属表）。须在入队前物化，worker 不回源画布 |
| `attempt` | 已开始的 attempt 计数 |
| `max_attempts` | 上限；默认值在 brief 确认前不要写进代码 |
| `hold_id` | 关联钱包冻结单 |
| `idempotency_key` | 与用户一起唯一 |
| `cancel_requested_at` | 取消意图 |
| `error_code` | 映射后的稳定码，可空 |
| `result_object_keys` | TOS 键列表或指向结果表 |
| `created_at` / `updated_at` | 审计 |

**不放在 `job` 上的东西**：`queue_name`、`routing_key`、AMQP tag、Anytoken 用户 id、上游原始 message。

## `job_step`

一次 running 对应一行（或一次 attempt 一行）。

| 概念字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `job_id` | 所属任务 |
| `attempt` | 第几次 |
| `status` | `running` / `succeeded` / `failed` / `cancelled` |
| `error_code` | 映射码 |
| `provider_task_id` | 指向原文痕迹 |
| `started_at` / `ended_at` | 时间 |

## `outbox_message`

| 概念字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `aggregate_type` | 如 `job` |
| `aggregate_id` | `job_id` |
| `payload` | 投递信封，业务上只需要 `job_id` |
| `status` | `pending` / `sent` / `failed` |
| `created_at` / `sent_at` | 审计 |

dispatcher 只负责送到 RabbitMQ。重试间隔与失败处理在 WP3 brief 写明。

## `idempotency_record`

| 概念字段 | 说明 |
| --- | --- |
| `user_id` | 作用域 |
| `key` | 客户端幂等键 |
| `job_id` | 首次 submit 的结果 |
| `request_hash` | 可选；同一 key 不同 body 应拒绝，而不是静默复用 |

同一 key 重复 submit 返回同一 `job_id`，不二次冻结。

## `provider_task`

| 概念字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `job_id` / `job_step_id` | 关联 |
| `upstream_request_id` | Anytoken 或上游 id，可空 |
| `raw_status` | 上游状态 |
| `raw_body` | 原文（受限访问） |
| `created_at` | 审计 |

C 端 `query` / `result` **不** 包含 `raw_body`。
