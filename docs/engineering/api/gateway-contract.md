# 内部网关合同：submit / query / result

以下是 stage-1 内部 DTO 语义。JSON 字段名用 snake_case。实现时可加信封（`data` / `error`），但下面的业务字段不能被 Anytoken 字段名替换。

## 公共信封（建议）

```json
{
  "ok": true,
  "data": {},
  "error_code": null,
  "message": null
}
```

失败时 `ok=false`，`error_code` 为稳定码，`message` 为当前语言友好短句。不要把上游字符串填进 `message`。

## Submit

**用途**：创建一个可执行 job，并在同一事务冻结与写 outbox。

请求（概念）：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `idempotency_key` | 是 | 同一用户重复提交返回同一 job |
| `capability` | 是 | 如 `image.generate` |
| `model` | 是 | flora 内部模型标识 |
| `params` | 是 | 已物化参数（prompt、尺寸、引用的对象键等）。不要传整张画布文档 |
| `client_job_id` | 否 | 若提供，仅作客户端追踪，权威 id 仍由服务端返回 |

响应：

| 字段 | 说明 |
| --- | --- |
| `job_id` | 权威 id，也是以后 MQ 的 body |
| `status` | 通常 `queued`；冻失败则无 job 或立即失败（WP5 写死一种） |
| `hold_id` | 若已冻结 |
| `error_code` / `message` | 仅失败 |

**副作用**：余额不足 → 不入队。同一 `idempotency_key` + 同一请求哈希 → 不二次冻结。

## Query

**用途**：读状态，给画布轮询或刷新。不触发生成。

请求：`job_id`（路径或 query）。

响应：

| 字段 | 说明 |
| --- | --- |
| `job_id` | 回显 |
| `status` | 状态机枚举 |
| `attempt` / `max_attempts` | 便于 UI 显示重试 |
| `error_code` / `message` | 失败时的友好信息 |
| `updated_at` | 避免无变化空转时可配合条件请求 |

**不包含**：`raw_body`、Anytoken 请求、供应商渠道名、堆栈。

## Result

**用途**：成功后取输出引用；未成功时与 query 类似，仍不带原文。

响应：

| 字段 | 说明 |
| --- | --- |
| `job_id` | 回显 |
| `status` | 必须先看状态，再读 outputs |
| `outputs` | 成功时：`[{ "type": "image", "object_key": "...", "url": "短期读地址" }]` |
| `error_code` / `message` | 非成功 |

`url` 由 api 用 TOS 签名，短 TTL。worker 只写 `object_key`。

## Cancel

| 字段 | 说明 |
| --- | --- |
| `job_id` | 要取消的任务 |

已终态：幂等返回当前状态。运行中：置 `cancel_requested`；最终 `cancelled` 并 release。用户得到 `JOB_CANCELLED` 或成功取消的友好句，而不是上游 cancel 原文。

## Worker 内部（不是 HTTP C 端）

队列消息：

```json
{ "job_id": "..." }
```

Worker 调 Anytoken 的请求/响应 **不** 定义为本合同的一部分。映射发生在基础设施层：上游失败 → `error_code` + `provider_task.raw_body`。

## 画布侧怎么用

1. 用户点生成 → web 收集节点上已有参数 → `submit`。
2. 画布用 `job_id` 轮询 `query`（间隔在 WP6 brief 确认，禁止实现者随手写死长轮询风暴）。
3. `succeeded` → `result` 拉 URL → 写入节点展示。
4. 失败 → 展示 `message` + 可复制的 `error_code`（给客服），不展示 raw。
