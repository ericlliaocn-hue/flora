# Feature brief：WP4 Job 状态机、Outbox、RabbitMQ

- 日期：2026-10-24
- 作者：pilipala engineering
- 对应 WP / 里程碑：WP4 / M1
- 状态：已确认（待实现）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax（上方面包屑硬约束一并保留）。不要摘要。

本任务落地 **Job 域第一段可跑通实现**：`job` / `job_step` / `outbox_message` / `idempotency_record` + 状态机 + 事务内 outbox + RabbitMQ 投递（消息体只有 `job_id`）+ C 端 submit / query / result / cancel（继承 WP3 鉴权闸门）。**不做钱包冻结、不做 Anytoken、不做真实 worker 消费出图。**

## 背景

WP0–WP3 已合入 main（当前含 WP3：`users` + Sa-Token + Flyway `V1__create_users.sql` + Redis 会话 + `/api/v1/**` 闸门）。`api` 已有 `art.pilipala.api.job` 空包与 `package-info`；`/api/v1/jobs/**` 已被 `AuthPathPolicy` 保护（无 token → `UNAUTHORIZED`；有 token 尚无控制器 → 404）。

[DEVELOPMENT.md](../DEVELOPMENT.md) WP4：

> `job` / `job_step` / `outbox_message` / 幂等键；状态机与列级定义见 [data-model/jobs.md](../data-model/jobs.md)；事务内写 outbox，再投递；消息体只有 `job_id`。
>
> 状态：`created → queued → running → succeeded`；`failed`（`attempt < max` 则回 `queued`）；`cancelled`；`dead`（DLQ）。
>
> 不做：在 job 表写死 queue 名、routing key、RabbitMQ 协议字段作为领域模型。
>
> 完成：单测或集成能走通成功、重试、取消、死信；换一个假 Transport 也不必改状态枚举。

WP1 曾 **刻意不引** `spring-boot-starter-amqp`，避免 worker 无 broker 时 actuator rabbit health 标 DOWN。本 WP **只在 `api` 引入 amqp**；`worker` 本 WP **不加** amqp 依赖（真实消费 + Anytoken 是 WP5）。

## 必须遵守

- [ADR 0003](../adr/0003-rabbitmq-first-queue-agnostic-jobs.md)：MQ body = `job_id`；job 表不写 queue / routing / amqp_*
- [ADR 0004](../adr/0004-thin-worker-no-god-classes.md)：瘦 worker；禁止 TaskCenter 上帝类；本 WP 不在 worker 拉画布 / 钱包 / Anytoken
- [ADR 0005](../adr/0005-friendly-errors-vs-raw-upstream.md)：C 端 `error_code` + 友好 `message`；禁止异常原文出站
- ADR 0002：业务 API = Java 21 + Spring Boot 3
- TECH_STACK.md：Postgres 权威；Redis 短锁；RabbitMQ 第一期
- [data-model/jobs.md](../data-model/jobs.md)：列级照抄源（禁止改名改语义加禁止列）
- [data-model/errors.md](../data-model/errors.md)：只用已登记码；本 WP 允许启用的码见下方「拍板」
- [api/gateway-contract.md](../api/gateway-contract.md)：信封与 submit/query/result/cancel 字段
- CONTRIBUTING.md：只做 brief 范围；不改 docs/content/**
- WP3 已拍板：HTTP 状态码统一 200 + ok 布尔；失败看 ok=false + error_code。本 WP 继承，禁止改回 401/404 当业务失败信号（资源不存在用信封 JOB_NOT_FOUND，HTTP 仍 200）

## 本 WP 拍板（已决定，禁止再猜）

| 项 | 锁定值 |
| --- | --- |
| 表 | 仅 job / job_step / outbox_message / idempotency_record。不建 provider_task（WP5）、不建 wallet*（WP6）、不建 job_lease |
| 迁移 | Flyway `V2__create_job_domain.sql`（接在 WP3 的 `V1__create_users.sql` 之后） |
| hold_id | 列按 jobs.md 建（UUID NULL）；本 WP 不建 wallet_hold，故不加指向 wallet_hold 的物理 FK；submit 后 hold_id 恒 null |
| 钱包 | 整包推迟 WP6。WP4 submit 不 freeze；queued 在本 WP 表示「已入队可被领取」，暂不要求「已 freeze」。WP6 再把 freeze 接回同一事务 |
| MQ body | 仅 `{"job_id":"<uuid>"}`；可加传输层 event_id（= outbox id）；禁止 params / 图片 / 钱包额 |
| Transport | 接口 `JobMessageTransport`（或同名）；实现：RabbitJobTransport + FakeJobTransport（测试）。换 Transport 不改状态枚举 |
| amqp 依赖 | 只加在 api/pom.xml：`spring-boot-starter-amqp`。worker/pom.xml 不加 |
| api 与 broker | 本 WP 起 api 本地需要 Postgres + Redis + RabbitMQ（outbox dispatcher 投递）。actuator：对 rabbit 健康检查，broker 未起则 api health 可为 DOWN——诚实写进 runbook；不要给 worker 加 rabbit 健康检查 |
| 领取 / lease | 权威在 Postgres（queued → running + attempt++）；短锁 Redis `SET job:lease:{job_id} {worker_id} NX EX {seconds}`，默认 TTL 60，环境变量 `PILIPALA_JOB_LEASE_TTL_SECONDS`；禁止拍脑袋改默认且不写文档 |
| 谁领取 | 本 WP 提供 api 侧 ClaimJob（或等价应用服务）+ 测试内 FakeWorkerDriver 调用它。不在本 WP 让 worker 进程消费；WP5 再接消费者调同一服务合同 |
| max_attempts | 默认 3（与 jobs.md 一致）；禁止静默改默认 |
| 重试可见态 | 失败后：先写 job.status=failed（中间可见），若 attempt < max_attempts 则同事务或紧随迁回 queued 并再写 outbox；对 query：允许短暂看到 failed 或已回 queued——实现选一种，PR / 测试写死并验收 |
| attempt 用尽 | → dead（终态）；Transport 侧对应 DLQ 配置（队列参数），不是 job 表列 |
| 取消 | 非终态：写 cancel_requested_at；若仍 queued/created → 直接 cancelled；若 running → 保留意图，Claim/驱动下一步时看到意图则 cancelled 并结束 step。已终态：幂等返回当前状态 |
| HTTP 路径 | 见下方 API 节（锁定） |
| 信封 | 与 WP3 / gateway-contract 一致；HTTP 始终 200；失败 ok=false |
| 本 WP 启用的 error_code | 仅：UNAUTHORIZED、VALIDATION_ERROR、JOB_NOT_FOUND、JOB_CANCELLED、IDEMPOTENCY_CONFLICT、JOB_FAILED。不要启用 WALLET_INSUFFICIENT / PROVIDER_UNAVAILABLE（无钱包 / 无上游）。不要发明新码 |
| 错误文案 | 临时 zh+en 静态 Map（可新建 JobErrorMessages，风格对齐 AuthErrorMessages）；WP9 再迁 i18n_message。禁止 e.getMessage() |
| SQL | JdbcTemplate，不引入 ORM（与 WP3 一致） |
| result / TOS | 本 WP outputs[].object_key 来自 job.result_object_keys；url 一律省略或 null（WP8 再签名）。成功路径可由 FakeWorkerDriver 写入假 object_key 列表以验收 |
| 幂等 | UNIQUE(user_id, key)；同 key 同 request_hash → 返回同一 job_id，不二次入队；同 key 不同 body → IDEMPOTENCY_CONFLICT |
| request_hash | 对规范化后的 capability+model+params JSON（建议 canonical JSON / SHA-256 hex）计算；算法写进代码注释与 PR |

## 本 WP 不拍板（禁止发明，留给后续 brief）

- 钱包 freeze/confirm/release、WALLET_INSUFFICIENT 入队拦截（WP6）
- Anytoken 客户端、provider_task 表与原文入库（WP5）
- worker 进程真实 `@RabbitListener` 消费与 ack/nack 策略细节的生产化（WP5；本 WP 只在 api 测 FakeTransport + FakeWorkerDriver）
- TOS 上传与签名 URL（WP8）
- 前端改打 pilipala API（WP7）
- i18n_message 表与运营改文案（WP9）
- 超时扫回收 running 孤儿的完整运维策略（可留接口/TODO；若做最小扫，默认阈值必须写进本 brief——见「留给实现者」）
- 多队列 / 按 capability 分 routing（禁止在 job 表写 queue；Transport 配置可先单队列）
- Admin / DLQ 运营 UI（WP11）

## 目标

1. Flyway V2 建齐四张表，列与 data-model/jobs.md 一致（hold_id 无物理 FK；user_id FK → users.id 建议建上）。
2. 登录用户可 `POST /api/v1/jobs/submit` → 同一事务写入 job（经 created→queued）、idempotency_record、outbox_message(pending)；返回 job_id + status=queued；hold_id 为 null。
3. Outbox dispatcher 将 pending 投递到 RabbitMQ（或测试 FakeTransport），payload 含 job_id；成功后 outbox → sent。
4. 可 query / result / cancel；他人 job 或乱 id → JOB_NOT_FOUND（不泄露存在性）。
5. 单测或集成（推荐 Testcontainers Postgres+Redis+Rabbit 或 嵌入 FakeTransport + 本机已有依赖）走通：成功、重试回 queued、取消、dead（含 DLQ 或 FakeTransport 等价记录）。
6. 换 FakeJobTransport 跑同一套状态机测试，不改状态枚举与 job 表。
7. runbooks/local-dev.md 写清：api 需要 Postgres + Redis + RabbitMQ；给出 Rabbit 启动、env、curl 验收；诚实标注无钱包 / 无 Anytoken / worker 仍不消费。
8. 本 brief 落盘，并更新 feature-briefs/README.md 索引行。

## 范围

### 允许新增 / 修改

| 路径 | 动作 |
| --- | --- |
| `docs/engineering/feature-briefs/2026-10-24-wp4-job-outbox-rabbit.md` | 新增（本文件全文落入仓） |
| `docs/engineering/feature-briefs/README.md` | 索引表加 WP4 一行；可将 WP3 行状态改为「已实现，待验收」若仍写「实现中」（可选，不强制改 WP3 验收结论） |
| `api/pom.xml` | 增加 spring-boot-starter-amqp；测试可用 Testcontainers（postgres/rabbitmq/redis）——可选但推荐 |
| `pom.xml`（父） | 仅当需要管理测试容器版本时；不要升级 Spring Boot / Sa-Token 大版本 |
| `api/src/main/resources/db/migration/V2__create_job_domain.sql` | 四表 + 索引 + CHECK |
| `api/src/main/resources/application.yml` | RabbitMQ `${ENV:}`、outbox 轮询间隔、lease TTL、exchange/queue Transport 配置（不是领域列） |
| `api/src/main/resources/application-local.yml` | 本地非密钥默认（localhost broker）；禁止真实密码 |
| `api/src/main/java/com/pilipala/api/job/**` | web / application / domain / infra（仓储、状态机、outbox、transport、lease、错误文案） |
| `api/src/main/java/com/pilipala/api/common/ErrorCodes.java` | 追加本 WP 启用的已登记码常量（名称必须与 errors.md 一致） |
| `api/src/main/java/com/pilipala/api/job/package-info.java` | 更新 WP 注释 |
| `api/src/test/java/com/pilipala/api/job/**` | 状态机 + 幂等 + outbox/FakeTransport + API 切片测试 |
| `docker-compose.deps.yml` | 追加 RabbitMQ 服务（仍不是全家桶）；密码/默认用户走环境变量或官方默认 guest 仅本地并在 runbook 标明 |
| `docs/engineering/runbooks/local-dev.md` | WP4 启动、Rabbit、curl、Fake vs 真 broker 说明；更新「api 依赖」表 |
| `docs/engineering/data-model/README.md` | job 四表「首次落地」可标「WP4 已落地」并链到本 brief（不要改列定义） |
| `worker/.../job/package-info.java` | 仅注释：消费在 WP5；本 WP 不改 worker 依赖 |
| `.gitignore` | 仅必要时小补 |

### 建议 Java 包（可微调命名，职责必须拆开，禁止上帝类）

```
art.pilipala.api.job
  web/           JobController, JobExceptionHandler（或复用/扩展公共异常 → 信封）
  application/   SubmitJob, GetJob, GetJobResult, CancelJob, ClaimJob,
                 RecordAttemptResult（成功/失败/死信迁移）, OutboxDispatchTick
  domain/        Job, JobStatus, JobStep, 状态迁移纯函数/小服务（非法迁移抛领域异常）
  infra/         JobRepository, JobStepRepository, OutboxRepository, IdempotencyRepository,
                 RedisJobLease, RabbitJobTransport, FakeJobTransport,
                 OutboxDispatcher, RabbitConfig（声明 exchange/queue/DLQ——仅配置）,
                 JobErrorMessages, JobException
```

- Controller 不写 SQL、不直接碰 Rabbit Template。
- Application 不依赖 Servlet API。
- **禁止** `JobCenterService` / 单类数千行协调器。
- 不要把 job 逻辑塞进 `auth` / `billing` / `gateway` 包。

## 非范围

- `provider_task` 表、Anytoken HTTP、上游 raw 入库
- `wallet` / `wallet_hold` / `wallet_ledger`、freeze/confirm/release、`WALLET_INSUFFICIENT`
- 给 `worker` 加 `spring-boot-starter-amqp`、真实消费者、画布依赖、钱包确认
- 在 `job` 表增加 `queue_name` / `routing_key` / `amqp_*` / Anytoken user id / raw message
- Postgres `job_lease` 表
- TOS / STS / 签名 URL
- 前端 `web/`、登录页、改打 pilipala API
- Admin、RBAC、DLQ 运营台
- 新建未在 errors.md 登记的 `error_code`
- 提前建 `i18n_message` 表
- `docs/content/**`、公开 Fumadocs、把工程进度写进用户可见 CHANGELOG（本 WP 不要改 CHANGELOG.md / docs/content/docs/progress/*——C 端用户尚不可见托管提交）
- NewAPI / 双账本 / 胖 worker
- 升级 Spring Boot / 换 Sa-Token 大版本 / 引入 ORM

## 数据表字段

照抄 [data-model/jobs.md](../data-model/jobs.md)。下列为迁移必须实现的摘要；有冲突以 jobs.md 为准。

### `job`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK；默认 `gen_random_uuid()` | = MQ `job_id` |
| `user_id` | UUID | NO | FK → `users.id`；IDX (`user_id`, `created_at` DESC) | |
| `status` | TEXT | NO | CHECK ∈ `created`\|`queued`\|`running`\|`succeeded`\|`failed`\|`cancelled`\|`dead`；IDX (`status`, `updated_at`) | |
| `capability` | TEXT | NO | | 如 `image.generate` |
| `model` | TEXT | NO | | |
| `params` | JSONB | NO | | 已物化 |
| `attempt` | INT | NO | DEFAULT 0；CHECK >= 0 | |
| `max_attempts` | INT | NO | DEFAULT 3；CHECK >= 1 | |
| `hold_id` | UUID | YES | **本 WP 无 FK** | 恒 null 直至 WP6 |
| `idempotency_key` | TEXT | NO | UNIQUE (`user_id`, `idempotency_key`) | |
| `cancel_requested_at` | TIMESTAMPTZ | YES | | |
| `error_code` | TEXT | YES | | |
| `result_object_keys` | JSONB | YES | | 如 `["img/..."]` |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

**禁止列**：`queue_name`、`routing_key`、`amqp_*`、Anytoken user id、上游 raw。

### `job_step`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `job_id` | UUID | NO | FK → `job.id` ON DELETE CASCADE；UNIQUE (`job_id`, `attempt`) | |
| `attempt` | INT | NO | CHECK >= 1 | |
| `status` | TEXT | NO | CHECK ∈ `running`\|`succeeded`\|`failed`\|`cancelled` | |
| `error_code` | TEXT | YES | | |
| `provider_task_id` | UUID | YES | **本 WP 无 FK**（`provider_task` 表未建） | 恒 null 直至 WP5 |
| `started_at` | TIMESTAMPTZ | NO | | |
| `ended_at` | TIMESTAMPTZ | YES | | |

### `outbox_message`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `aggregate_type` | TEXT | NO | | 固定可用 `job` |
| `aggregate_id` | UUID | NO | 部分索引：`WHERE status='pending'` 上 (`status`, `created_at`) 或等价 | 通常 = `job_id` |
| `payload` | JSONB | NO | | 至少 `{"job_id":"..."}`；建议含 `event_id` |
| `status` | TEXT | NO | CHECK ∈ `pending`\|`sent`\|`failed` | |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `sent_at` | TIMESTAMPTZ | YES | | |

### `idempotency_record`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `user_id` | UUID | NO | | |
| `key` | TEXT | NO | UNIQUE (`user_id`, `key`) | |
| `job_id` | UUID | NO | FK → `job.id` | |
| `request_hash` | TEXT | NO | | |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

### 不建

- `provider_task`（WP5）
- `wallet*`（WP6）
- `job_lease`（永远不建；用 Redis）

## API

公共信封（成功 / 失败），HTTP **200**：

```json
{ "ok": true, "data": {}, "error_code": null, "message": null }
```

认证：除注明外，全部需要有效 pilipala-token 头（WP3 闸门已覆盖 `/api/v1/jobs/**`）。`user_id` 只从会话取，禁止信任请求体里的 user id。

### `POST /api/v1/jobs/submit`

请求：

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `idempotency_key` | string | 是 | 非空；建议长度 ≤ 128 |
| `capability` | string | 是 | 非空；本 WP 不校验模型目录，只做非空与长度上限（建议 ≤ 64） |
| `model` | string | 是 | 非空；建议 ≤ 128 |
| `params` | object | 是 | JSON object；禁止 null；可 `{}` |
| `client_job_id` | string | 否 | 若提供，不代替服务端 job_id；本 WP 可不落库（若落库须先改 data-model——默认不落库） |

成功 data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `job_id` | string (uuid) | |
| `status` | string | 通常 queued |
| `hold_id` | string (uuid) \| null | 本 WP 恒 null |

失败：

| error_code | 何时 |
| --- | --- |
| `UNAUTHORIZED` | 无会话 |
| `VALIDATION_ERROR` | 缺字段 / 类型错 / 空 key |
| `IDEMPOTENCY_CONFLICT` | 同 key 不同 body |

同 key 同 body：`ok=true`，返回原 job_id（不新建 outbox / 不二次入队）。

事务顺序（锁定）：

1. 鉴权得 user_id
2. 算 request_hash；查 idempotency → 冲突或重放则返回
3. 同一事务：INSERT job（status=created，attempt=0，hold_id=null）→ 更新为 queued → INSERT outbox_message(pending, payload={job_id, event_id?}) → INSERT idempotency_record
4. 提交后由 dispatcher 异步投递（测试可同步泵一次）

### `GET /api/v1/jobs/{job_id}`

成功 data：

| 字段 | 类型 |
| --- | --- |
| `job_id` | string (uuid) |
| `status` | string |
| `attempt` | integer |
| `max_attempts` | integer |
| `error_code` | string \| null |
| `message` | string \| null |
| `updated_at` | string (ISO-8601) |

`message`：若 job.error_code 非空，用 JobErrorMessages 解析；否则 null。

非本人或不存在 → JOB_NOT_FOUND。

无 raw / 无 provider 字段。

### `GET /api/v1/jobs/{job_id}/result`

成功 data：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `job_id` | string (uuid) | |
| `status` | string | 先看状态再读 outputs |
| `outputs` | array | 仅 succeeded 时非空有意义；元素 `{ "type": "image", "object_key": "...", "url": null }` |
| `error_code` / `message` | string \| null | 非成功时可有 |

`type`：本 WP 若只有假图片键，固定 `image` 即可。

非本人 / 不存在 → JOB_NOT_FOUND。

### `POST /api/v1/jobs/{job_id}/cancel`

无请求体（或忽略体）。成功 data：至少含 job_id、status（当前态）。

已终态：幂等返回当前态，ok=true。

取消完成后用户可见：status=cancelled，后续 query 的 error_code 可为 JOB_CANCELLED（写入 job.error_code）。

非本人 / 不存在 → JOB_NOT_FOUND。

## 状态机

```
created → queued → running → succeeded
                ↘ failed ──(attempt < max_attempts)→ queued
                ↘ cancelled          （终态）
                ↘ dead               （终态，attempt 用尽或标记不可重试）
```

| 迁移 | 谁触发（本 WP） | 附带 |
| --- | --- | --- |
| → created → queued | SubmitJob 同事务 | 写 outbox pending |
| queued → running | ClaimJob（测试 FakeWorkerDriver 调） | attempt++；插 job_step(running)；Redis lease |
| running → succeeded | RecordAttemptResult | step ended；写 result_object_keys；不 confirm 钱包 |
| running → failed → queued | 同上，可重试 | step failed；新 outbox pending |
| running → failed → dead | attempt 用尽或不重试 | step failed；Transport DLQ 等价 |
| → cancelled | CancelJob 或 Claim 见取消意图 | 释放 lease；不 release 钱包（无 hold） |

非法迁移（如 succeeded → running）必须拒绝并打日志；C 端若撞上用 VALIDATION_ERROR 或保持幂等——优先幂等/安全拒绝，禁止静默改状态。

## MQ

```json
{ "job_id": "<uuid>" }
```

Transport 配置建议默认（可经 env 覆盖，写入 yml + runbook）：

| 项 | 建议默认 |
| --- | --- |
| exchange | `pilipala.jobs`（topic 或 direct，实现选一种写死） |
| routing key | `job.execute` |
| queue | `pilipala.jobs.execute` |
| DLQ | `pilipala.jobs.execute.dlq` |
| outbox 轮询 | 每 `PILIPALA_OUTBOX_POLL_MS`（默认 500）扫一批 pending |

这些名字 **只** 存在于配置与 Rabbit 声明，禁止写入 job 行。

## 验收

- [ ] 存在 `docs/engineering/feature-briefs/2026-10-24-wp4-job-outbox-rabbit.md`，与本 brief 一致（可微调措辞；不可改表列名 / 不可改禁止列 / 不可发明 error_code / 不可给 worker 加 amqp）
- [ ] feature-briefs/README.md 有 WP4 行
- [ ] Flyway `V2__create_job_domain.sql` 建四表；无 provider_task / 无 wallet* / 无 job_lease；hold_id 与 provider_task_id 无悬空物理 FK
- [ ] `POST /api/v1/jobs/submit` 需登录；无 token → UNAUTHORIZED（HTTP 200 + ok=false）
- [ ] submit 成功：job.status=queued，hold_id null，有 idempotency 行，有 outbox（先 pending 后 sent，或测试泵送后 sent）
- [ ] MQ / FakeTransport 消息体解析后含且业务上只需 job_id
- [ ] 同 idempotency_key + 同 body 重放 → 同一 job_id，不双插 job
- [ ] 同 key 不同 body → IDEMPOTENCY_CONFLICT
- [ ] query / result / cancel 对非本人 → JOB_NOT_FOUND
- [ ] FakeWorkerDriver（或等价测试）：claim → succeed → query succeeded，result 含 object_key
- [ ] 失败重试：attempt 递增；未达 max → 回 queued 且再次投递；达 max → dead
- [ ] cancel：queued 可直接 cancelled；running 尊重 cancel_requested_at 最终 cancelled；终态幂等
- [ ] FakeJobTransport 与 RabbitJobTransport 可切换，状态枚举代码无 Rabbit 协议字段
- [ ] job 表无 queue_name / routing_key / amqp_*
- [ ] Redis lease 键格式 `job:lease:{job_id}`；默认 TTL 60 可经 env 配
- [ ] ErrorCodes 仅追加 errors.md 已有码；文案无堆栈 / 无 SQL 原文
- [ ] api 引入 amqp；worker 未引入 amqp、无消费者、无 Anytoken、无画布依赖
- [ ] 无钱包逻辑、无 TOS、无前端改动、无 docs/content/**、无 CHANGELOG 用户叙事
- [ ] runbooks/local-dev.md 含 RabbitMQ 启动、env、至少一组 curl（register/login → submit → query）
- [ ] docker-compose.deps.yml 含 rabbitmq（或 runbook 明确 Homebrew 路径且 compose 可选——二者至少一种可复制）
- [ ] PR 描述引用本 brief 路径；验收自勾；写明故意没做（钱包/Anytoken/worker 消费/TOS/前端）
- [ ] 分支 `feat/wp4-job-outbox-rabbit`；PR base = main；不 force-push 他人分支；无真实密钥

## 非目标

- 不在本任务证明真生图或 Anytoken 连通
- 不在本任务扣费 / 充值
- 不把「能 submit」写成「托管 pilipala 已可用」
- Admin / 运营改 DLQ 不是本任务

## 给 Minimax 的额外约束

- 先读再写：DEVELOPMENT.md（WP4）、TECH_STACK.md、data-model/jobs.md、data-model/errors.md、api/gateway-contract.md、ADR 0003 / 0004 / 0005、本 brief、现有 api/auth/**（信封 / 闸门 / 异常风格）、V1__create_users.sql、AuthPathPolicy、ApiResponse、ErrorCodes、runbooks/local-dev.md。
- 只在 api 做 Job API + outbox + Transport；worker 本 WP 保持瘦空壳。
- 分层：Controller 不写 SQL；Application 不依赖 HTTP 类型；禁止上帝类。
- 中文为主写 brief / 注释；代码标识符英文；JSON snake_case；error_code 大写蛇形；状态枚举小写。
- 新的超时 / 重试 / 限流 / 批次大小：必须出现在本 brief 或下方「留给实现者」选项内，禁止静默加经验值。
- 本地依赖：文档化 Postgres + Redis + RabbitMQ；更新 deps compose；不要假装「仍只需 JDK」。
- 做完用简短中文列：改了哪些模块、V2 建了哪些表、curl 怎么验、故意没做哪些（钱包/Anytoken/worker 消费/TOS/前端）。
- 密钥与真实密码不要写进文档样例。

### 留给实现者的选择（最多拍这几项，PR 写明选了哪样）

- 测试底座：Testcontainers（Postgres + Redis + Rabbit）或 本机 Homebrew 服务 + FakeTransport 单测为主 + 少量手工 curl。二者都须覆盖成功/重试/取消/dead。
- 失败重试的 query 可见态：失败后对外短暂保持 failed 直到迁回 queued，或 同事务直接回 queued（用户很少看到 failed）。选一种写进测试。
- outbox 投递失败：标记 outbox failed 并靠后续 tick 重试（简单计数即可，默认最多再试 10 次后停在 failed 打日志），或 保持 pending 靠下次 poll——选一种；不要引入复杂退避库 unless 写进 PR 说明且默认值写在 yml。
- 不要再自行扩大其它开放问题。

## 分支 / Commit / PR 指令

```bash
git fetch origin
git checkout main
git pull origin main
git checkout -b feat/wp4-job-outbox-rabbit

# 1) 落入 brief + README 索引
# 2) V2 迁移 + job 包实现 + amqp 仅 api + 测试 + runbook + deps compose
git add docs/engineering/feature-briefs api pom.xml docs/engineering/runbooks docker-compose.deps.yml docs/engineering/data-model/README.md worker
# 不要 add 密钥文件；不要改 docs/content 与 CHANGELOG
git commit -m "WP4: Job 状态机、Outbox 与 RabbitMQ 投递"
git push -u origin feat/wp4-job-outbox-rabbit
```

开 PR → main：

- 标题：`WP4: Job 状态机 + Outbox + RabbitMQ`
- 正文必须含：brief 路径 `docs/engineering/feature-briefs/2026-10-24-wp4-job-outbox-rabbit.md`、做了什么、验收自勾、故意没做（无钱包/无 Anytoken/无 worker 消费/无 TOS/无前端）、本地如何起 Postgres/Redis/Rabbit 与 curl、实现者三选一结论

不要自行 merge；等对照 brief 严格验收后再合。

## 环境变量提示（写入 runbook，勿填真实值）

```bash
# 继承 WP3
export PILIPALA_API_PORT=8080
export PILIPALA_TOKEN_NAME=pilipala-token
export PILIPALA_TOKEN_TIMEOUT=2592000
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/pilipala
export SPRING_DATASOURCE_USERNAME=pilipala
export SPRING_DATASOURCE_PASSWORD=replace-me
export SPRING_DATA_REDIS_HOST=127.0.0.1
export SPRING_DATA_REDIS_PORT=6379

# WP4 RabbitMQ（Spring AMQP 默认属性名，可按实现微调但须文档一致）
export SPRING_RABBITMQ_HOST=127.0.0.1
export SPRING_RABBITMQ_PORT=5672
export SPRING_RABBITMQ_USERNAME=guest
export SPRING_RABBITMQ_PASSWORD=guest
export PILIPALA_JOB_LEASE_TTL_SECONDS=60
export PILIPALA_OUTBOX_POLL_MS=500
```

## curl 验收草稿（实现者可微调，须写进 runbook）

```bash
API=http://127.0.0.1:8080/api/v1
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password1"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# submit
curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"k1","capability":"image.generate","model":"demo","params":{"prompt":"hi"}}'
# → ok=true, data.job_id, data.status=queued, data.hold_id=null

# query
curl -s $API/jobs/$JOB_ID -H "pilipala-token: $TOKEN"

# 无 token
curl -s -X POST $API/jobs/submit -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"k2","capability":"image.generate","model":"demo","params":{}}'
# → ok=false, error_code=UNAUTHORIZED

# cancel
curl -s -X POST $API/jobs/$JOB_ID/cancel -H "pilipala-token: $TOKEN"
```

成功 / 重试 / dead 的完整路径以自动化测试为主；runbook 可注明「claim/succeed 由测试 FakeWorkerDriver 驱动，WP5 前不要指望 worker 进程改状态」。
