# Feature brief：WP2 阶段 1 表/字段级 Schema 文档

- 日期：2026-10-24
- 作者：flora engineering
- 对应 WP / 里程碑：WP2 / M1
- 状态：已实现，待验收

## 背景

WP0 白标与 WP1 API/worker 空骨架之后，仓内已有 **概念级** 数据模型：

- [data-model/jobs.md](../data-model/jobs.md)
- [data-model/billing.md](../data-model/billing.md)
- [api/gateway-contract.md](../api/gateway-contract.md)
- [i18n-and-errors.md](../i18n-and-errors.md)

这些页多处写「字段级在 WP3/WP5/WP8 brief 再定」。稳扎稳打要求：先把表 / 字段 / 索引 / 枚举 / 错误码锁死进工程文档，再开认证、Flyway、状态机代码。否则每个实现 WP 都会重新发明列。

### 必须遵守

- [ADR 0001](../adr/0001-anytoken-not-newapi-fork.md)：只用 Anytoken；禁止 NewAPI fork / 双账本
- [ADR 0003](../adr/0003-rabbitmq-first-queue-agnostic-jobs.md)：MQ body = `job_id`；job 表不写 queue / routing
- [ADR 0004](../adr/0004-thin-worker-no-god-classes.md)：瘦 worker
- [ADR 0005](../adr/0005-friendly-errors-vs-raw-upstream.md)：C 端 `error_code` + 友好文案 vs ops 原文
- [TECH_STACK.md](../TECH_STACK.md)：Postgres 权威；Redis 会话 / 短锁；预付费 freeze / confirm / release
- [CONTRIBUTING.md](../CONTRIBUTING.md)：只做 brief 范围；不改 `docs/content/**`

### 关于 WP 编号（必须改 DEVELOPMENT）

当前 [DEVELOPMENT.md](../DEVELOPMENT.md) 把「认证」写成 WP2、「Job+Outbox」写成 WP3。本 brief 按约定插入 **WP2 = 阶段 1 Schema 文档**。请把原 WP2–WP10 顺延为 **WP3–WP11**，并同步改依赖图与各节标题。

[data-model/README.md](../data-model/README.md)、[api/README.md](../api/README.md) 里「首次落地 / 何时」的 WP 号一并顺延（认证 → WP3，Job → WP4，钱包 → WP6，错误表 → WP9，Admin → WP11）。

本 PR 只改文档里的编号引用，**不写任何实现代码**。

## 目标

1. Job 域表字段定稿：`job` / `job_step` / `outbox_message` / `idempotency_record` / `provider_task`（类型、空否、索引、FK、注释）。
2. 锁定状态机枚举与迁移规则（含 `failed` 非终态、用尽 → `dead`）。
3. Billing 域表字段定稿：`wallet` / `wallet_hold` / `wallet_ledger`（单账本；freeze / confirm / release）。
4. Gateway 内部 DTO（submit / query / result / cancel）字段名、类型、必填、规范化说明写死；强调 thin hop，不是 Anytoken 协议拷贝。
5. 错误模型定稿：C 端字段 vs ops 字段；预留 `error_code` 表；`i18n_message` 列级 schema。
6. 声明 flora 的「lease」等价物：Postgres **不建** lease 表；worker 领取靠 `status=running` + Redis 短锁（键约定写入 jobs.md）。
7. 新增本 brief 落盘，并更新 [feature-briefs/README.md](./README.md) 索引行。

上述全部是 Markdown；零 `.java` / `.sql` / Flyway / `web/` 业务改动。

## 范围

允许新增 / 修改：

| 路径 | 动作 |
| --- | --- |
| `docs/engineering/feature-briefs/2026-10-24-wp2-phase1-schema.md` | 新增（本文件全文落入仓） |
| `docs/engineering/feature-briefs/README.md` | 索引表加 WP2 一行；修正「不要提前写空 brief」旁注（本行是真实 brief） |
| `docs/engineering/DEVELOPMENT.md` | 插入 WP2 Schema；原 WP2–WP10 顺延 WP3–WP11；更新依赖图 |
| `docs/engineering/data-model/README.md` | 标明字段级已由 WP2 锁；更新「首次落地」WP 号；去掉「类型 TBD」措辞 |
| `docs/engineering/data-model/jobs.md` | 重写 / 扩写为表 + 字段级（见下方强制 schema） |
| `docs/engineering/data-model/billing.md` | 重写 / 扩写为表 + 字段级 |
| `docs/engineering/data-model/errors.md` | 新增：`error_code` 注册表 + 与 `i18n_message` / `provider_task` 关系 |
| `docs/engineering/api/gateway-contract.md` | 补字段类型 / 必填 / 规范化；保持 snake_case |
| `docs/engineering/api/README.md` | WP 号顺延；链到 errors.md |
| `docs/engineering/i18n-and-errors.md` | 与 errors.md 对齐；列级指向 data-model；删「TBD」 |
| `docs/engineering/MILESTONES.md` | 仅当文中仍写「概念表」处改为「字段级见 data-model（WP2）」；勿改里程碑退出语义 |

## 非范围

- 任何 `api/**/*.java`、`worker/**/*.java` 业务实现（含 Entity、Repository、Flyway）
- `**/db/migration/**`、裸 `.sql` 迁移文件
- RabbitMQ 连接、消费者、DLQ 代码（文档可写 Transport 配置默认建议值，但标「实现 WP 再落代码」）
- Sa-Token 登录接口 / 会话代码（`users` 表字段可在 data-model 预留一小节，标「实现于 WP3 认证」）
- Anytoken HTTP 客户端、TOS、STS、回调
- 前端、`docs/content/**`、Admin UI
- Docker compose（Postgres / Redis / Rabbit）
- 改 `CHANGELOG.md`（用户无感知；进度记 brief + 索引）
- 发明第二套账本、NewAPI 表、`queue_name` 业务列、上帝类设计

## 数据表字段（强制稿 · 实现者照抄进 docs，禁止改名改语义）

### 约定

- PostgreSQL；PK 默认 UUID + `gen_random_uuid()`
- 时间一律 `TIMESTAMPTZ`
- 金额一律 `BIGINT`（flora credit 整数，禁止 float）
- 字符串枚举存 `TEXT` + `CHECK`（或实现 WP 再用 PG enum，文档先写允许值）
- JSON 用 `JSONB`

本 WP 只把下列表写进 Markdown，**不建库**。

### 1) `job`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | 亦即 MQ 消息体里的 `job_id` |
| `user_id` | UUID | NO | IDX (`user_id`, `created_at` DESC) | 提交者；FK → `users.id`（users 表 WP3 建） |
| `status` | TEXT | NO | CHECK ∈ 下表枚举；IDX (`status`, `updated_at`) | 状态机权威 |
| `capability` | TEXT | NO | | 如 `image.generate` |
| `model` | TEXT | NO | | flora 内部模型名 |
| `params` | JSONB | NO | | 入队前已物化；worker 不回源画布 |
| `attempt` | INT | NO | DEFAULT 0；CHECK >= 0 | 已开始的 attempt 数 |
| `max_attempts` | INT | NO | DEFAULT 3；CHECK >= 1 | 默认 3；实现 WP 不得静默改默认，除非改本文档 |
| `hold_id` | UUID | YES | FK → `wallet_hold.id` | 一 job 一 hold；未冻前可空 |
| `idempotency_key` | TEXT | NO | UNIQUE (`user_id`, `idempotency_key`) | 与用户一起唯一 |
| `cancel_requested_at` | TIMESTAMPTZ | YES | | 取消意图 |
| `error_code` | TEXT | YES | | 映射后的稳定码 |
| `result_object_keys` | JSONB | YES | | 成功时 TOS object_key 列表，如 `["img/..."]` |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

**禁止出现的列**：`queue_name`、`routing_key`、`amqp_*`、Anytoken user id、上游 raw message。

#### Lease 等价物（写入 jobs.md 正文）

- **不建 `job_lease` 表。**
- Worker 领取：事务内 `status: queued → running` 且 `attempt = attempt + 1`；同时 Redis `SET job:lease:{job_id} {worker_id} NX EX {seconds}`（建议默认 TTL 60，实现 WP 写进 brief 可调，**禁止在代码里拍脑袋**）。
- 锁丢失但 DB 仍为 `running`：靠超时扫 + attempt / max 规则回收；细节实现 WP 写验收，本页只锁「**权威在 Postgres，短锁在 Redis**」。

### 2) `job_step`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `job_id` | UUID | NO | FK → `job.id` ON DELETE CASCADE；UNIQUE (`job_id`, `attempt`) | |
| `attempt` | INT | NO | CHECK >= 1 | |
| `status` | TEXT | NO | CHECK ∈ `running`\|`succeeded`\|`failed`\|`cancelled` | |
| `error_code` | TEXT | YES | | |
| `provider_task_id` | UUID | YES | FK → `provider_task.id` | |
| `started_at` | TIMESTAMPTZ | NO | | |
| `ended_at` | TIMESTAMPTZ | YES | | |

### 3) `outbox_message`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `aggregate_type` | TEXT | NO | | 如 `job` |
| `aggregate_id` | UUID | NO | IDX (`status`, `created_at`) 部分索引 pending | 通常为 `job_id` |
| `payload` | JSONB | NO | | 业务上必须含 `{"job_id":"<uuid>"}`；可加 `event_id`（= outbox id）供投递幂等 |
| `status` | TEXT | NO | CHECK ∈ `pending`\|`sent`\|`failed` | |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `sent_at` | TIMESTAMPTZ | YES | | |

dispatcher 只投递；重试间隔实现 WP 再定。换 RocketMQ 不改本表语义。

### 4) `idempotency_record`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `user_id` | UUID | NO | | |
| `key` | TEXT | NO | UNIQUE (`user_id`, `key`) | 客户端幂等键 |
| `job_id` | UUID | NO | FK → `job.id` | 首次 submit 结果 |
| `request_hash` | TEXT | NO | | 同一 key 不同 body → `IDEMPOTENCY_CONFLICT` |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

### 5) `provider_task`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `job_id` | UUID | NO | IDX (`job_id`)；FK → `job.id` | |
| `job_step_id` | UUID | YES | FK → `job_step.id` | |
| `upstream_request_id` | TEXT | YES | | Anytoken / 上游 id |
| `raw_status` | TEXT | YES | | 上游状态 |
| `raw_body` | JSONB | YES | | 原文；C 端 query / result 禁止返回 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

### 6) `wallet`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `user_id` | UUID | NO | UNIQUE | 一用户一钱包 |
| `available` | BIGINT | NO | DEFAULT 0；CHECK >= 0 | 可用 credit |
| `frozen` | BIGINT | NO | DEFAULT 0；CHECK >= 0 | 已冻未确认 |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

**不变量**：变更只经由 hold / ledger 路径，禁止「直接改两个数字」。

### 7) `wallet_hold`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | = `job.hold_id` |
| `user_id` | UUID | NO | IDX (`user_id`, `created_at`) | |
| `job_id` | UUID | YES | UNIQUE 可空 | 默认与 job 同事务创建 |
| `amount` | BIGINT | NO | CHECK > 0 | 冻结额 |
| `status` | TEXT | NO | CHECK ∈ `frozen`\|`confirmed`\|`released` | 终态只进一次 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `finalized_at` | TIMESTAMPTZ | YES | | confirm / release 时间 |

重放 confirm / release 必须幂等（实现 WP6 验收）。

### 8) `wallet_ledger`（stage-1 必建，不再 TBD）

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `wallet_id` | UUID | NO | FK → `wallet.id`；IDX (`wallet_id`, `created_at`) | |
| `user_id` | UUID | NO | | 冗余便于对账 |
| `type` | TEXT | NO | CHECK ∈ `topup`\|`freeze`\|`confirm`\|`release` | |
| `amount` | BIGINT | NO | CHECK != 0 | 符号约定见正文状态机段落，避免实现者猜 |
| `hold_id` | UUID | YES | FK → `wallet_hold.id` | |
| `job_id` | UUID | YES | | |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

**单账本**：禁止 Anytoken 侧再记用户额度并双写。

### 9) `i18n_message`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `key` | TEXT | NO | UNIQUE (`key`, `locale`) | 常与 `error_code` 相同 |
| `locale` | TEXT | NO | CHECK ∈ `zh`\|`en` | stage-1 仅这两种 |
| `template` | TEXT | NO | | 短句；有限占位符 |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

### 10) `users`（仅字段预留，实现 WP3）

在 [data-model/README.md](../data-model/README.md) 或新建极短 `users.md` 中列出最少列，标「WP3 认证实现」：

| 字段 | 类型 | 空 | 说明 |
| --- | --- | --- | --- |
| `id` | UUID | NO | PK |
| `username` | TEXT | NO | UNIQUE |
| `password_hash` | TEXT | NO | Sa-Token 登录用；哈希算法 WP3 brief 定 |
| `locale` | TEXT | YES | `zh` / `en` 偏好 |
| `created_at` / `updated_at` | TIMESTAMPTZ | NO | |

不要在本 WP 设计社交登录、角色矩阵。

## 状态机（强制写入 jobs.md）

```
created → queued → running → succeeded
                ↘ failed ──(attempt < max_attempts)→ queued
                ↘ cancelled          （终态，release）
                ↘ dead               （终态，attempt 用尽或不可重试，release）
```

| 状态 | 终态？ | 谁写 | 钱包 |
| --- | --- | --- | --- |
| `created` | 否 | api | 可尚未 freeze |
| `queued` | 否 | api / 重试迁移 | 已 freeze（可执行任务） |
| `running` | 否 | worker | 保持 freeze |
| `succeeded` | 是 | 状态迁移 | confirm |
| `failed` | 否（可见中间态） | worker 汇报后 | 保持 freeze；若 `attempt < max` → 回 `queued` |
| `cancelled` | 是 | api / worker | release |
| `dead` | 是 | 状态迁移 | release |

**锁死选择（原 jobs.md 开放项）**：attempt 用尽或不重试 → 进入 `dead`，**不以 `failed` 作终态停留**。对用户 query：重试中可展示「失败重试中」（status 仍可能短暂为 `failed` 或已回 `queued`，实现 WP 选一种并测）；不可恢复则 `dead`。

### MQ 消息体

```json
{ "job_id": "<uuid>" }
```

可加传输层 `event_id`；禁止塞 `params` / 图片 / 钱包额。

## API（文档合同 · 无代码）

升级 [gateway-contract.md](../api/gateway-contract.md)，字段一律 snake_case。公共信封：

```json
{ "ok": true, "data": {}, "error_code": null, "message": null }
```

| 表面 | 关键请求字段 | 关键响应字段 |
| --- | --- | --- |
| `submit` | `idempotency_key`(必填 string)、`capability`(必填)、`model`(必填)、`params`(必填 object)、`client_job_id`(可选) | `job_id`(uuid)、`status`、`hold_id`(可选)、失败时 `error_code` + `message` |
| `query` | `job_id` | `job_id`、`status`、`attempt`、`max_attempts`、`error_code`、`message`、`updated_at`；无 raw |
| `result` | `job_id` | `job_id`、`status`、`outputs[]`（`type`、`object_key`、`url?`）、错误字段；无 raw |
| `cancel` | `job_id` | 当前状态；幂等 |

### 规范化规则（必须写进正文）

- flora 内部字段名 ≠ Anytoken 字段名；映射只发生在 gateway 适配层。
- `message` 只来自 `i18n_message` 模板，禁止填上游字符串。
- Worker → Anytoken 的 HTTP 不是本合同的一部分。

## 错误模型（强制写入 errors.md + 对齐 i18n-and-errors.md）

- C 端默认响应字段：`error_code`（稳定大写蛇形）、`message`（当前 locale 短句）。
- Ops 字段：日志与 `provider_task.raw_status` / `raw_body`；仅 M4 / WP11 admin 可见。

### 预留码（名称本 WP 锁死；文案模板可 WP9 再填）

| error_code | 何时 |
| --- | --- |
| `WALLET_INSUFFICIENT` | 冻结失败 |
| `JOB_NOT_FOUND` | 不存在或非本人 |
| `JOB_CANCELLED` | 取消 |
| `JOB_FAILED` | 已映射的泛化业务失败 |
| `PROVIDER_UNAVAILABLE` | Anytoken / 上游不可用或未识别 |
| `IDEMPOTENCY_CONFLICT` | 同 key 不同 body |
| `UNAUTHORIZED` | 无会话 |
| `VALIDATION_ERROR` | 请求字段不合法 |

未识别上游 → 泛化码 + 原文入库；禁止 `e.getMessage()` 进 `message`。

## 验收

- [ ] 存在 `docs/engineering/feature-briefs/2026-10-24-wp2-phase1-schema.md`，内容与本 brief 一致（可微调措辞，**不可改表名 / 枚举 / 禁止列**）
- [ ] `feature-briefs/README.md` 有 WP2 行，状态「已实现，待验收」或「实现中」
- [ ] `DEVELOPMENT.md`：WP2 = 本 Schema 文档；原认证 / Job / … 已顺延为 WP3–WP11；依赖图已更新
- [ ] `data-model/jobs.md` 含上表全部列、状态机、Redis lease 约定、禁止列清单
- [ ] `data-model/billing.md` 含 `wallet` / `wallet_hold` / `wallet_ledger` 全列与 freeze → confirm / release 语义；写明单账本
- [ ] 新增 `data-model/errors.md`；`i18n-and-errors.md` 与之无矛盾、无「列类型 TBD」
- [ ] `api/gateway-contract.md` 含类型 / 必填 / 规范化；MQ 示例仅 `job_id`
- [ ] `data-model/README.md` 表清单与「首次落地」WP 号已顺延；声明字段级由 WP2 锁定
- [ ] 未新增任何 `.java` 业务代码、`.sql`、Flyway、compose、前端改动、`docs/content/**`
- [ ] 未引入 NewAPI 表、双账本、`queue_name` 业务字段、Postgres lease 表
- [ ] PR 描述引用本 brief 路径；列出「做了什么 / 故意没做什么」
- [ ] 单 commit 或干净 docs commits；分支名 `docs/wp2-phase1-schema`；PR base = `main`
- [ ] 不 force-push 他人分支；无密钥

## 非目标

- 不在本任务证明状态机跑通（那是 WP4+）
- 不在本任务登录、扣费、出图
- 不把工程文档搬进公开 `docs/content/`
- 不抄 qingfeng 表结构；只 critique（无 3k LOC 上帝表设计、无双适配器）

## 给 Minimax 的额外约束

- **先读再写**：MILESTONES.md、DEVELOPMENT.md、data-model/*、api/gateway-contract.md、i18n-and-errors.md、ADR 0001 / 0003 / 0004 / 0005、本 brief。
- 在现有 Markdown 上扩写，**不要另造平行目录**（如 `docs/schema/`）。
- 中文为主，表头可用中英；枚举值、列名、`error_code` 用英文蛇形 / 大写蛇形。
- 金额禁止 DECIMAL / FLOAT；统一 BIGINT credit。
- 新的超时 / 重试 / 限额：只允许在文档里写「建议默认」并标注实现 WP；禁止改 Java 配置「顺便加上」。
- 若与 main 上 WP1 的 `feature-briefs/README.md` 冲突：两行都保留（WP1 + WP2）。
- 做完用简短中文列：改了哪些文档、顺延了哪些 WP 号、故意没建哪些表 / 没写哪些代码。
- 密钥、真实 Anytoken URL / Key 不要写进文档样例；用占位符。

## 分支 / Commit / PR 指令

```bash
git checkout -b docs/wp2-phase1-schema   # 已从最新 main
# …只改 docs/engineering/**
git add docs/engineering
git commit -m "WP2: 阶段1 表/字段级 schema 文档（job/billing/gateway/errors）"
git push -u origin docs/wp2-phase1-schema
```

开 PR → `main`：

- 标题：`WP2: 阶段1 表/字段级 Schema 文档`
- 正文必须含：brief 路径、文档清单、验收自勾、故意没做（无 Flyway / 无 Java / 无前端）、WP 编号顺延说明

不要 merge 自己；等对照 brief 验收后再合。
