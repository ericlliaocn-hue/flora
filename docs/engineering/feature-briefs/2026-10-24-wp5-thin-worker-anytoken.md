# Feature brief：WP5 瘦 Worker + Anytoken 唯一适配器

- 日期：2026-10-24
- 作者：pilipala engineering
- 对应 WP / 里程碑：WP5 / M1（假任务闭环）& M2（真生图适配器边界）
- 状态：已确认（待实现）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax（上方面包屑硬约束一并保留）。不要摘要。

本任务落地 Worker 真实消费 + 唯一 Anytoken 网关适配器 + provider_task 原文留痕表：worker 进程监听 RabbitMQ `pilipala.jobs.execute`（消息体只有 `job_id`），抢 Redis 短锁 `job:lease:{job_id}`，将 Postgres job 从 queued 推进到 running，调用唯一 Anytoken 适配器（支持本地默认 stub 模式与真实 http 模式），写入 provider_task 与 job_step，并完成 succeeded / 可重试回 queued（写 outbox） / cancelled / dead（进 DLQ）的完整闭环。不做钱包 freeze/confirm/release、不做火山 TOS 上传与签名 URL、不做前端改动。

## 背景

WP0–WP4 已合入 main（当前最新为 WP4 `51a7045`）：

- api 已有认证闸门（WP3）与 Job 域四表 `V2__create_job_domain.sql`（`job` / `job_step` / `outbox_message` / `idempotency_record`）、状态机、Outbox 轮询投递器 `OutboxDispatcher`、RabbitMQ 拓扑声明 `RabbitConfig`（exchange `pilipala.jobs`、routing key `job.execute`、queue `pilipala.jobs.execute`、DLQ `pilipala.jobs.execute.dlq`）。
- WP4 刻意没改 worker：`worker/pom.xml` 目前只有 web + actuator，本地 curl 提交的 job 投进 RabbitMQ 后会一直停在 `queued`。

WP4 review 留下两条非阻塞收口项，一并在本 WP 收掉：

1. running 期间若用户点了取消（`cancel_requested_at != null`），即使上游恰好返回成功，收尾时也必须尊重取消意图，迁入 `cancelled`（`error_code = JOB_CANCELLED`），而不是 `succeeded`。
2. `requestCancel` 写 `cancel_requested_at` 时同步刷新 `updated_at = now()`。

DEVELOPMENT.md WP5：

> 里程碑：M1（假任务）/ M2（真生图）
>
> 做什么：Worker 消费 job_id，加载 job，调唯一 Anytoken 适配器，写 job_step 与 provider_task。第一期可用 echo/假上游证明状态机，但适配器边界必须一次做对。
>
> 禁止：TaskCenterService / CanvasAgentProductionService 式大类；worker 拉画布节点、拼工作流、确认钱包；NewAPI 第二适配器。
>
> 完成：worker 模块编译依赖里看不到 canvas/creation UI；一个供应商适配器入口。

## 必须遵守

- ADR 0001：模型流量 **只**经过 Anytoken；禁止 fork/内嵌 NewAPI；禁止第二套供应商适配器；禁止双用户/双额度
- ADR 0003：MQ 消息体只有 `job_id`（及传输层可选 `event_id`）；job 表不存 queue / routing / `amqp_*`
- ADR 0004：瘦 worker；禁止 TaskCenterService / CanvasAgentProductionService 上帝类；worker 不读画布、不编排多节点工作流、不碰钱包
- ADR 0005：C 端只看稳定 `error_code` + 本地化友好短句；上游 HTTP 状态、响应原文、堆栈只进 `provider_task` 与服务端日志，绝不出 C 端
- TECH_STACK.md：Java 21 + Spring Boot 3；Postgres 权威；Redis 短锁；RabbitMQ 第一期
- [data-model/jobs.md](../data-model/jobs.md)：`provider_task` 与 `job_step.provider_task_id` 的列级照抄源（禁止改列名或私自加列）
- [data-model/errors.md](../data-model/errors.md)：本 WP 启用 `PROVIDER_UNAVAILABLE`（追加到 ErrorCodes 与 JobErrorMessages）；禁止发明未登记码
- [api/gateway-contract.md](../api/gateway-contract.md)：C 端 query / result 严禁包含 `raw_body`、Anytoken 请求、渠道名或堆栈
- CONTRIBUTING.md：只做 brief 范围；不改 `docs/content/**` 与 `CHANGELOG.md`

## 本 WP 拍板（已决定，禁止再猜）

| 项 | 锁定值 |
| --- | --- |
| Maven 模块结构 | 保持根工程仅 api 与 worker 两个模块，禁止新建第三个 Maven 子模块，禁止让 worker 依赖 pilipala-api 可执行 jar |
| worker 进程形态 | 无状态 Spring Boot 进程（端口默认 8081），直连 PostgreSQL + Redis + RabbitMQ（JDBC + Redis 短锁 + `@RabbitListener`）。worker 不引 Sa-Token、不暴露 C 端业务 Controller、不跑 Flyway（迁移统一由 api 管理） |
| 迁移 V3 | `api/src/main/resources/db/migration/V3__create_provider_task.sql`：建 `provider_task` 表（列名与 data-model/jobs.md 逐字一致），并给 `job_step.provider_task_id` 补上指向 `provider_task(id)` 的物理外键 |
| provider_task 列名 | 严格照抄 data-model/jobs.md：`id`、`job_id`、`job_step_id`、`upstream_request_id`、`raw_status`、`raw_body`、`created_at`（共 7 列，禁止改名或自造 `provider` / `http_status` / `updated_at` 等未登记列；结构化摘要统一放进 `raw_body` JSONB） |
| 外键写入顺序 | 1) Claim 时插入 `job_step(running)`（此时 `provider_task_id` 为 NULL）；2) 调完 Anytoken 后在收尾事务内先 `INSERT INTO provider_task (job_id, job_step_id, ...)` 拿到 `provider_task.id`；3) 再 `UPDATE job_step SET status=?, error_code=?, provider_task_id=?, ended_at=now()`。无循环外键冲突 |
| 事务与慢调用边界 | **严禁**在持有数据库事务期间发起 Anytoken HTTP/stub 调用。拆成三次短操作：① claim 短事务（queued → running + 插 job_step）→ ② 事务外调 AnytokenGatewayAdapter → ③ complete 短事务（插 provider_task + 更新 job_step + 更新 job + 必要时插 outbox_message） |
| Redis 短锁 | 键格式 `job:lease:{job_id}`，值 workerId，TTL 默认 60 秒（`PILIPALA_JOB_LEASE_TTL_SECONDS`）。在 claim 前获取，在 complete 结束后 finally 用 Lua 脚本按 owner 释放。若 Redis 锁已被其它 worker 持有，当前消费者跳过执行并 ack（防并发双跑） |
| RabbitMQ 消费与并发 | 监听队列 `pilipala.jobs.execute`；prefetch 固定默认 1（`spring.rabbitmq.listener.simple.prefetch=${PILIPALA_WORKER_PREFETCH:1}`）。worker 也声明同一套幂等持久化 exchange/queue/DLQ 拓扑，保证 worker 先于 api 启动也不报错 |
| 重试投递机制 | 当 attempt 失败且 `retryable == true` 且 `attempt < max_attempts` 时，complete 事务内同事务将 job 从 running → failed → queued（保留 `job.error_code`），并向 `outbox_message` 插入一条 pending 记录（payload = `{"job_id":"...","event_id":"..."}`），由 outbox 机制再次投递，**不在 worker 线程里 Thread.sleep 阻塞重试** |
| dead 与 DLQ | 当 `!retryable` 或 `attempt >= max_attempts` 时，complete 事务先提交 `job.status = 'dead'`（及 job_step / provider_task）；**在数据库事务提交之后**，让该条 MQ 消息进入死信队列 `pilipala.jobs.execute.dlq`（例如在非 `@Transactional` 的 listener 外层抛 `AmqpRejectAndDontRequeueException`，或显式投递到 DLQ 后 ack——**严禁因抛异常把已写好的 dead 状态回滚掉！**）。无法解析 job_id 的毒丸消息同样直接进 DLQ |
| 取消收口（含 WP4 备注修复） | 1) claim 时若 `job.status == queued` 且 `cancel_requested_at != null` → 直接迁 cancelled（`error_code = JOB_CANCELLED`），不调 Anytoken；2) complete 时重读 job，若 `cancel_requested_at != null`，无论本次 Anytoken 返回成功还是失败，job_step 与 job 一律落 cancelled（`error_code = JOB_CANCELLED`），`result_object_keys` 不写；3) requestCancel 同步更新 `updated_at = now()` |
| 唯一网关适配器 | 接口 `AnytokenGateway`（或 `AnytokenClient`），位于 `art.pilipala.worker.gateway`。整个仓库只允许这一个模型供应商适配器，禁止 NewAPI / OpenAI 直连第二适配器 |
| 适配器双模式（stub / http） | 配置项 `pilipala.gateway.mode=${PILIPALA_GATEWAY_MODE:stub}`：<br>1) **stub**（默认）：不发外部网络请求，按 params 约定（见下方「Anytoken 适配器规范」）确定性返回成功 / 可重试失败（`PROVIDER_UNAVAILABLE`）/ 不可重试失败（`JOB_FAILED`），让本地 curl 与集成测试零成本跑通全链路；<br>2) **http**：用 JDK 21 HttpClient 或 Spring RestClient 调 `${PILIPALA_GATEWAY_BASE_URL}`，带 `Authorization: Bearer ${PILIPALA_GATEWAY_API_KEY}`，超时 `${PILIPALA_GATEWAY_TIMEOUT_MS:30000}` |
| 错误映射与脱敏 | 上游 HTTP 4xx（非 429）默认映射 `JOB_FAILED`（retryable=false）；HTTP 429 / 5xx / 网络超时 / 连接失败 / 未知响应格式统一映射 `PROVIDER_UNAVAILABLE`（retryable=true）。`raw_body` 截断上限 4096 字符，且**严禁**把 `PILIPALA_GATEWAY_API_KEY` 或 `Authorization` 头写进 `provider_task` 或日志 |
| C 端错误文案 | api 的 ErrorCodes 新增 `PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE"`；JobErrorMessages 新增：<br>- zh：「生成服务暂时不可用，请稍后重试。」<br>- en：「The generation service is temporarily unavailable. Please try again later.」 |
| 成功结果物化 | 本 WP 尚未接 TOS（WP8），成功时写入合成或上游返回的 `result_object_keys`（如 `["anytoken/img/<job_id>/<attempt>.png"]`）；`GET /api/v1/jobs/{job_id}/result` 返回该 object_key，`url` 仍为 null |

## 本 WP 不拍板（禁止发明，留给后续 brief）

- 钱包 `wallet` / `wallet_hold` / `wallet_ledger` 及 freeze / confirm / release（WP6）
- 火山 TOS 上传、STS、签名短期读 url（WP8）
- 前端 `web/` 改打 pilipala API（WP7）
- `i18n_message` 数据库表（WP9）
- Admin 查看 `provider_task.raw_body` 与死信重投界面（WP11）
- 运行中超时孤儿任务的定时扫描回收器（保持接口或后续运维 brief，不在本 WP 自造定时扫表参数）

## 目标

1. Flyway `V3__create_provider_task.sql` 建 `provider_task` 表并补齐 `job_step.provider_task_id` 外键，列定义与 data-model/jobs.md 逐字一致。
2. api 启用 `PROVIDER_UNAVAILABLE` 错误码与中英文友好文案；修复 WP4 两处取消收口细节（`RecordAttemptResult` 成功分支检查 `cancel_requested_at`、`requestCancel` 刷新 `updated_at`）。
3. worker 引入 amqp、jdbc、postgresql、data-redis，配置 prefetch=1，实现瘦消费者：从 `pilipala.jobs.execute` 收到 `{"job_id":"..."}` → 抢 Redis 短锁 → queued → running → 调唯一 AnytokenGatewayAdapter → 写 `provider_task` + `job_step` + job（成功 / 重试写 outbox / 取消 / dead 进 DLQ）。
4. 唯一适配器支持 stub（默认）与 http 两模式；http 模式通过本地内置 HTTP 桩（JDK HttpServer 或 MockWebServer）完成单测/集成测试覆盖（200 成功、4xx 不可重试 JOB_FAILED、5xx/超时可重试 PROVIDER_UNAVAILABLE、响应原文截断与脱敏）。
5. 本地同时起 api（8080）与 worker（8081）时，用 curl 提交任务即可自动被 worker 消费并推进到 succeeded 或 dead，不再停在 queued。
6. worker 编译依赖树与源码中 **零** `web/`、画布、创作编排、钱包、Sa-Token、NewAPI 代码。
7. 更新 runbooks/local-dev.md、data-model/README.md、feature-briefs/README.md。

## 范围

### 允许新增 / 修改

| 路径 | 动作 |
| --- | --- |
| `docs/engineering/feature-briefs/2026-10-24-wp5-thin-worker-anytoken.md` | 新增（本文件全文落入仓） |
| `docs/engineering/feature-briefs/README.md` | 索引表新增 WP5 行；将 WP4 状态更新为「已实现，待验收」 |
| `docs/engineering/data-model/README.md` | 将 `provider_task` 标为「WP5 已落地」（不改列定义） |
| `docs/engineering/runbooks/local-dev.md` | 更新 worker 依赖表（Postgres + Redis + RabbitMQ）、环境变量、双进程启动与端到端 curl 验收步骤 |
| `api/src/main/resources/db/migration/V3__create_provider_task.sql` | 新增 `provider_task` 表、索引、以及 `job_step.provider_task_id` 外键 |
| `api/src/main/java/com/pilipala/api/common/ErrorCodes.java` | 追加 `PROVIDER_UNAVAILABLE` |
| `api/src/main/java/com/pilipala/api/job/infra/JobErrorMessages.java` | 追加 `PROVIDER_UNAVAILABLE` 的 zh / en 短句 |
| `api/src/main/java/com/pilipala/api/job/application/RecordAttemptResult.java` | 修复：succeed 前若 `job.cancelRequestedAt() != null` 则走 cancelled；支持关联 `provider_task_id`（若 api 侧测试也覆盖） |
| `api/src/main/java/com/pilipala/api/job/infra/JdbcJobRepository.java` | 修复：requestCancel 同步更新 `updated_at = now()` |
| `api/src/test/java/com/pilipala/api/job/**` | 补充 `PROVIDER_UNAVAILABLE` 文案与运行中取消覆盖成功结果的单测 |
| `worker/pom.xml` | 引入 spring-boot-starter-amqp、spring-boot-starter-jdbc、postgresql、spring-boot-starter-data-redis、spring-boot-starter-test |
| `worker/src/main/resources/application.yml` | 配置 Postgres、Redis、RabbitMQ（prefetch: 1）、`pilipala.job.*`、`pilipala.gateway.*`（全部 `${ENV:}` 占位，`spring.flyway.enabled: false`） |
| `worker/src/main/resources/application-local.yml` | 本地非密钥默认值（`pilipala.gateway.mode: stub` 等） |
| `worker/src/main/java/com/pilipala/worker/**` | 瘦 worker 实现（见下方建议包结构） |
| `worker/src/test/java/com/pilipala/worker/**` | 消费者 + 状态机闭环测试、AnytokenGatewayAdapter（stub + http 本地 HttpServer）测试、Redis 短锁测试 |

### 建议 worker Java 包结构（职责拆开，禁止上帝类）

```
art.pilipala.worker
  WorkerApplication.java
  gateway/
    AnytokenGateway.java              （唯一供应商适配器接口）
    AnytokenGatewayAdapter.java       （实现 stub 与 http 两种模式，归一为内部结果）
    AnytokenProperties.java           （绑定 pilipala.gateway.*：mode / baseUrl / apiKey / timeoutMs）
    AnytokenRequest.java              （入参：jobId, attempt, capability, model, paramsJson）
    AnytokenAttemptResult.java        （出参：succeeded, retryable, errorCode, outputObjectKeys,
                                        upstreamRequestId, rawStatus, rawBodyJson）
  job/
    consumer/
      JobRabbitConsumer.java          （@RabbitListener 监听 pilipala.jobs.execute，解析 job_id,
                                        调 ExecuteJob，若返回 DEAD 或坏消息则触发进 DLQ）
      WorkerRabbitConfig.java         （幂等声明 exchange / queue / DLQ 拓扑）
    application/
      ExecuteJob.java                 （编排三段：claim 短事务 → 调 AnytokenGateway → complete 短事务）
      WorkerJobLifecycleService.java  （claim 与 complete 的 @Transactional 边界）
    domain/
      WorkerJob.java                  （最小只读/迁移视图，不含任何队列或画布字段）
      ProviderTaskRecord.java         （对应 provider_task 表）
    infra/
      WorkerJobRepository.java / JdbcWorkerJobRepository.java
      WorkerJobStepRepository.java / JdbcWorkerJobStepRepository.java
      ProviderTaskRepository.java / JdbcProviderTaskRepository.java
      WorkerOutboxRepository.java / JdbcWorkerOutboxRepository.java
      WorkerJobLease.java / RedisWorkerJobLease.java
```

- 禁止 `TaskCenterService` / `CanvasAgentProductionService` 式大类。
- **禁止**在 `worker` 中引入 `sa-token`、`web/`、画布、钱包表。
- 仓储定义成接口（`WorkerJobRepository` 等），单测可用内存实现跑状态机，生产用 `Jdbc*` 实现（与 WP4 测试风格保持一致）。

## 非范围

- `wallet` / `wallet_hold` / `wallet_ledger`、freeze / confirm / release、`WALLET_INSUFFICIENT`（WP6）
- 火山引擎 TOS SDK、上传生成图、签名短期 URL（WP8）
- 前端 `web/` 任何改动（WP7）
- `i18n_message` 表（WP9）
- Admin 后台与 DLQ 重投 UI（WP11）
- 第二个模型网关适配器（NewAPI / OpenAI 直连 / 供应商多路路由）
- `docs/content/**`、Fumadocs、`CHANGELOG.md`

## 数据表字段

照抄 [data-model/jobs.md](../data-model/jobs.md)。本 WP 新增 `V3__create_provider_task.sql`，**严禁修改 V1 / V2 已有迁移文件**。

### `provider_task`（V3 新增）

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK；DEFAULT `gen_random_uuid()` | |
| `job_id` | UUID | NO | FK → `job.id` ON DELETE CASCADE；IDX (`job_id`) | |
| `job_step_id` | UUID | YES | FK → `job_step.id` ON DELETE SET NULL | 关联本次 attempt 的 step |
| `upstream_request_id` | TEXT | YES | | Anytoken / 上游请求标识 |
| `raw_status` | TEXT | YES | | 上游状态（如 `"200"`、`"503"`、`"TIMEOUT"`） |
| `raw_body` | JSONB | YES | | 脱敏且截断（≤ 4096 字符）的上游响应摘要 JSON |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT `now()` | |

### `job_step` 外键补齐（V3 执行）

```sql
ALTER TABLE job_step
    ADD CONSTRAINT job_step_provider_task_id_fkey
    FOREIGN KEY (provider_task_id) REFERENCES provider_task (id);
```

C 端 `GET /api/v1/jobs/{job_id}` 与 `GET /api/v1/jobs/{job_id}/result` **严禁**查询或返回 `provider_task` 的任何字段。

## Anytoken 唯一适配器规范

### 1. 配置项（worker/src/main/resources/application.yml）

```yaml
pilipala:
  gateway:
    # stub = 本地确定性假上游（默认，无需真实密钥即可跑通）；http = 调真实 Anytoken HTTP 接口
    mode: ${PILIPALA_GATEWAY_MODE:stub}
    base-url: ${PILIPALA_GATEWAY_BASE_URL:http://127.0.0.1:18080}
    api-key: ${PILIPALA_GATEWAY_API_KEY:}
    timeout-ms: ${PILIPALA_GATEWAY_TIMEOUT_MS:30000}
```

### 2. stub 模式行为（锁死，供单测与本地 curl 零成本验收）

解析 job.params JSON 对象中的可选字段 `stub_outcome`（及可选 `stub_delay_ms`，范围 0..10000）：

| params.stub_outcome 取值 | 适配器返回 | 触发的状态机路径 |
| --- | --- | --- |
| 缺省 / 未提供 / `"succeeded"` | succeeded=true，outputObjectKeys=`["anytoken/img/<job_id>/<attempt>.png"]`，upstreamRequestId=`"stub-<job_id>-<attempt>"`，rawStatus=`"200"`，rawBody=`{"provider":"anytoken","mode":"stub","status":"succeeded","model":"<model>"}` | running → succeeded；job_step 为 succeeded；provider_task 落 1 行 |
| `"provider_unavailable"` | succeeded=false，retryable=true，errorCode=`"PROVIDER_UNAVAILABLE"`，upstreamRequestId=`"stub-<job_id>-<attempt>"`，rawStatus=`"503"`，rawBody=`{"provider":"anytoken","mode":"stub","http_status":503,"error":"upstream channel overloaded"}` | 第 1、2 次 running → failed → queued + 写 outbox_message(pending)；第 3 次（达 max_attempts=3）running → failed → dead + 消息进 DLQ；job_step 与 provider_task 共 3 行 |
| `"job_failed"` | succeeded=false，retryable=false，errorCode=`"JOB_FAILED"`，upstreamRequestId=`"stub-<job_id>-<attempt>"`，rawStatus=`"400"`，rawBody=`{"provider":"anytoken","mode":"stub","http_status":400,"error":"invalid generation parameters"}` | 第 1 次直接 running → failed → dead（不重试）+ 消息进 DLQ；job_step 与 provider_task 各 1 行 |

### 3. http 模式行为（真实 HTTP 适配边界）

请求：`POST {baseUrl}/v1/generations`（或可通过 `${PILIPALA_GATEWAY_GENERATE_PATH:/v1/generations}` 配置），请求头 `Content-Type: application/json`，若 apiKey 非空则带 `Authorization: Bearer {apiKey}`，请求体为内部归一化 JSON：`{"job_id":"...","attempt":1,"capability":"...","model":"...","params":{...}}`。

响应归一化：

- HTTP 2xx：提取 `upstream_request_id`（优先响应体 `id` / `request_id`，其次响应头 `x-request-id`）与输出列表（响应体 `object_keys` 或 `outputs[].object_key`；若上游未返则兜底生成 `["anytoken/img/<job_id>/<attempt>.png"]`），`succeeded = true`。
- HTTP 400 / 404 / 422 等客户端业务拒绝（4xx 且 != 429）：`succeeded = false`，`retryable = false`，`errorCode = "JOB_FAILED"`。
- HTTP 429 / 5xx / 网络异常 / 超时 / 非法 JSON：`succeeded = false`，`retryable = true`，`errorCode = "PROVIDER_UNAVAILABLE"`。

原文留痕与安全脱敏：

- `raw_status` 存 HTTP 状态码字符串（如 `"200"`、`"502"`）或异常类型（`"TIMEOUT"`、`"IO_ERROR"`）。
- `raw_body` 存 JSONB，例如 `{"http_status":502,"body_excerpt":"..."}`，响应体文本超过 **4096 字符**时截断并附 `"truncated":true`。
- **绝对禁止**将 apiKey、Authorization 头原样写入 `provider_task.raw_body` 或日志。

## Worker 消费与状态机流程（锁定）

```
RabbitMQ (pilipala.jobs.execute, prefetch=1)
   │  收到 {"job_id": "<uuid>", "event_id": "..."}
   ▼
1. 解析 job_id（若 JSON 损坏或非 UUID → 打 warn 日志，抛 AmqpRejectAndDontRequeueException 进 DLQ）
   │
   ▼
2. 抢 Redis 短锁 job:lease:{job_id} (NX EX 60s)
   ├─ 未抢到锁 → 说明另一 worker 正在处理同一 job，打 debug/info 日志，正常返回（ack）
   └─ 抢到锁（try ... finally 确保释放）
        │
        ▼
3. Claim 短事务 (@Transactional)：
   - 查 job：不存在则打 warn 返回；
   - 若 status == queued 且 cancel_requested_at != null → 更新 job 为 cancelled (JOB_CANCELLED)，提交并返回；
   - 若 status != queued（已 running / succeeded / cancelled / dead）→ 幂等跳过，返回；
   - UPDATE job SET status='running', attempt=attempt+1, updated_at=now() WHERE id=? AND status='queued'
   - INSERT INTO job_step (job_id, attempt, status) VALUES (?, attempt, 'running') RETURNING id
        │
        ▼
4. 事务外调用唯一 AnytokenGateway.execute(request) → 得到 AnytokenAttemptResult
        │
        ▼
5. Complete 短事务 (@Transactional)：
   → INSERT INTO provider_task (job_id, job_step_id, upstream_request_id, raw_status, raw_body) RETURNING id
   → 重新读取 job 最新行（检查运行期间是否收到 cancel_requested_at）
   → 分支 A（job.cancel_requested_at != null）：
       job_step → cancelled (error_code='JOB_CANCELLED', provider_task_id=ptId)
       job      → cancelled (error_code='JOB_CANCELLED')
   → 分支 B（result.succeeded() == true）：
       job_step → succeeded (error_code=null, provider_task_id=ptId)
       job      → succeeded (result_object_keys=..., error_code=null)
   → 分支 C（result.succeeded() == false 且 result.retryable() && job.attempt < job.max_attempts）：
       job_step → failed (error_code=result.errorCode(), provider_task_id=ptId)
       job      → failed → queued (error_code=result.errorCode())
       outbox   → INSERT pending (payload={"job_id":..., "event_id":...})
   → 分支 D（result.succeeded() == false 且不可重试或 attempt 已达 max_attempts）：
       job_step → failed (error_code=result.errorCode(), provider_task_id=ptId)
       job      → failed → dead (error_code=result.errorCode())
        │
        ▼
6. 事务提交后（finally 已释放 Redis lease）：
   - 若 Complete 结果为 DEAD → 让消息进入 pilipala.jobs.execute.dlq（因为 Complete 已在独立 @Transactional 方法中提交，此时抛 AmqpRejectAndDontRequeueException 不会回滚 DB！）
   - 其它情况正常结束方法（Spring AMQP 自动 ack）
```

## 验收

- [ ] 存在 `docs/engineering/feature-briefs/2026-10-24-wp5-thin-worker-anytoken.md`
- [ ] `feature-briefs/README.md` 有 WP5 行，WP4 状态更新为「已实现，待验收」
- [ ] Flyway `V3__create_provider_task.sql` 建 `provider_task` 表，7 个列名与 data-model/jobs.md 逐字一致，并补齐 `job_step.provider_task_id` 外键
- [ ] api 的 ErrorCodes 与 JobErrorMessages 包含 `PROVIDER_UNAVAILABLE`（含 zh / en 友好文案）
- [ ] WP4 备注修复已合入：running 期间 `cancel_requested_at != null` 时即使成功也收敛为 cancelled；requestCancel 刷新 `updated_at`
- [ ] worker 配置 `spring.rabbitmq.listener.simple.prefetch=1`（可通过 `PILIPALA_WORKER_PREFETCH` 覆盖）
- [ ] worker **只有**一个模型网关适配器（`AnytokenGateway` / `AnytokenGatewayAdapter`），无 NewAPI、无第二适配器
- [ ] stub 模式默认可跑：正常任务 queued → running → succeeded，写入 `result_object_keys`、`job_step(succeeded)`、`provider_task`
- [ ] stub 模式可重试失败（`stub_outcome = "provider_unavailable"`）：经历 3 次 attempt（attempt 1→2→3，前两次写 outbox_message 回 queued），第 3 次落 dead，`job.error_code = PROVIDER_UNAVAILABLE`，job_step 与 provider_task 均有 3 条记录，且 RabbitMQ `pilipala.jobs.execute.dlq` 收到死信
- [ ] stub 模式不可重试失败（`stub_outcome = "job_failed"`）：第 1 次 attempt 直接落 dead，`job.error_code = JOB_FAILED`，不二次重试
- [ ] http 模式经本地内置 HTTP 桩测试验证：200 成功、400 映射 JOB_FAILED（不重试）、503/超时映射 PROVIDER_UNAVAILABLE（可重试）、超长响应截断（≤ 4096 字符）、raw_body 不含 API Key
- [ ] C 端 `GET /api/v1/jobs/{job_id}` 与 `/result` 响应中 **绝不**出现 `raw_body`、`upstream_request_id`、上游英文错误原文
- [ ] Redis 短锁 `job:lease:{job_id}` 在真实消费流程中生效（抢锁 → 执行 → 释放；锁被占用时不重复执行）
- [ ] worker 编译依赖与包内无 `web/`、无 canvas/creation、无 wallet/billing、无 Sa-Token
- [ ] docs/engineering/runbooks/local-dev.md 更新 worker 启动依赖与端到端 curl 验收步骤
- [ ] 分支 `feat/wp5-thin-worker-anytoken`；PR base = main；无真实密钥入库；不要自行 merge

## 非目标

- 不在本 WP 对接真实线上付费 Anytoken 账单或扣减用户余额（钱包在 WP6）
- 不在本 WP 把生成图片上传到火山 TOS（TOS 在 WP8）
- 不在本 WP 提供死信队列重投管理后台（Admin 在 WP11）

## 给 Minimax 的额外约束

- 先读再写：DEVELOPMENT.md（WP5）、TECH_STACK.md、data-model/jobs.md、data-model/errors.md、api/gateway-contract.md、ADR 0001 / 0003 / 0004 / 0005、本 brief、现有 `api/src/main/java/com/pilipala/api/job/**`、`V2__create_job_domain.sql`、`worker/pom.xml`、`runbooks/local-dev.md`。
- 严守模块与事务边界：
  - 不要新建第三个 Maven 模块；保持 api 和 worker 两个模块。
  - 不要在 `@Transactional` 方法内部调 `AnytokenGateway.execute(...)`；数据库事务只包 claim 和 complete 两段纯 DB 读写。
  - 不要让 `@RabbitListener` 抛出的 `AmqpRejectAndDontRequeueException` 回滚掉 complete 已经提交的 `status = 'dead'`！确保 complete 在独立 `@Transactional` Bean 方法里先提交事务，再由外层 listener 决定 ack 或 reject-to-DLQ。
- 全部测试必须绿：`./mvnw test` 同时跑通 api 与 worker 全部单测。
- 做完用简短中文汇报：改了哪些模块、V3 建了什么、本地双进程 + curl 实测结果（成功 / 3 次重试进 dead+DLQ / 取消）、故意没做哪些（钱包 / TOS / 前端 / Admin）。

### 留给实现者的选择（最多拍这几项，PR 写明选了哪样）

- **http 模式 HTTP 客户端**：JDK 21 标准库 `java.net.http.HttpClient` 或 Spring Boot 3 内置 `RestClient`。二者都不需要引入额外第三方 HTTP 依赖；测试用 JDK 内置 `com.sun.net.httpserver.HttpServer` 即可零依赖测通真实 HTTP。
- **死信进入 `pilipala.jobs.execute.dlq` 的方式**：在 complete 独立事务提交 dead 之后，由外层 listener 抛 `AmqpRejectAndDontRequeueException` 触发队列自带的 `x-dead-letter-routing-key`（推荐），或显式发往 `pilipala.jobs.execute.dlq` 后正常 ack。无论选哪种，必须保证 DB 里 `job.status = 'dead'` 已持久化且 `pilipala.jobs.execute.dlq` 里有该消息。
- 不要再自行扩大其它开放问题。

## 分支 / Commit / PR 指令

```bash
git fetch origin
git checkout main
git pull origin main
git checkout -b feat/wp5-thin-worker-anytoken

# 1) 落入 brief + 更新 feature-briefs/README.md 与 data-model/README.md
# 2) api: V3 迁移 + PROVIDER_UNAVAILABLE + WP4 取消备注小修 + 单测
# 3) worker: 依赖 + 消费者 + 唯一 Anytoken 适配器(stub/http) + 状态机闭环 + 单测
# 4) 更新 runbooks/local-dev.md
git add docs/engineering/feature-briefs docs/engineering/data-model/README.md docs/engineering/runbooks/local-dev.md api worker
git commit -m "WP5: 瘦 Worker 消费者、唯一 Anytoken 适配器与 provider_task"
git push -u origin feat/wp5-thin-worker-anytoken
```

开 PR → main：

- 标题：`WP5: 瘦 Worker + Anytoken 唯一适配器 + provider_task`
- 正文必须含：brief 路径 `docs/engineering/feature-briefs/2026-10-24-wp5-thin-worker-anytoken.md`、做了什么、验收自勾、故意没做（无钱包 / 无 TOS / 无前端 / 无 NewAPI）、本地双进程 curl 实测记录、实现者二选一结论

不要自行 merge。

## 环境变量提示（写入 runbook，勿填真实值）

```bash
# api 与 worker 共用基础依赖
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/pilipala
export SPRING_DATASOURCE_USERNAME=pilipala
export SPRING_DATASOURCE_PASSWORD=replace-me
export SPRING_DATA_REDIS_HOST=127.0.0.1
export SPRING_DATA_REDIS_PORT=6379
export SPRING_RABBITMQ_HOST=127.0.0.1
export SPRING_RABBITMQ_PORT=5672
export SPRING_RABBITMQ_USERNAME=guest
export SPRING_RABBITMQ_PASSWORD=guest

# Job 队列与租约（队列名只是传输配置，不进 job 表）
export PILIPALA_JOB_EXCHANGE=pilipala.jobs
export PILIPALA_JOB_ROUTING_KEY=job.execute
export PILIPALA_JOB_QUEUE=pilipala.jobs.execute
export PILIPALA_JOB_DEAD_LETTER_QUEUE=pilipala.jobs.execute.dlq
export PILIPALA_JOB_LEASE_TTL_SECONDS=60

# WP5 Worker 与唯一 Anytoken 网关配置
export PILIPALA_WORKER_PORT=8081
export PILIPALA_WORKER_PREFETCH=1
export PILIPALA_GATEWAY_MODE=stub                        # stub | http
export PILIPALA_GATEWAY_BASE_URL=http://127.0.0.1:18080  # http 模式下生效
export PILIPALA_GATEWAY_API_KEY=                         # http 模式下由环境变量注入，勿入库
export PILIPALA_GATEWAY_TIMEOUT_MS=30000
```

## curl 端到端验收草稿（同时启动 api 与 worker，写进 runbook）

```bash
API=http://127.0.0.1:8080/api/v1
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password1"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 1) 正常生图任务（stub 默认成功）：submit 后被 worker 自动消费 → succeeded
JOB_OK=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp5-ok-1","capability":"image.generate","model":"demo","params":{"prompt":"a quiet garden"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 1
curl -s $API/jobs/$JOB_OK -H "pilipala-token: $TOKEN"
# → status="succeeded", attempt=1, error_code=null
curl -s $API/jobs/$JOB_OK/result -H "pilipala-token: $TOKEN"
# → status="succeeded", outputs=[{"type":"image","object_key":"anytoken/img/...","url":null}]

# 2) 上游暂时不可用（stub_outcome=provider_unavailable）：自动重试 3 次后进 dead + DLQ
JOB_RETRY=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp5-retry-1","capability":"image.generate","model":"demo","params":{"prompt":"fail","stub_outcome":"provider_unavailable"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 3
curl -s $API/jobs/$JOB_RETRY -H "pilipala-token: $TOKEN"
# → status="dead", attempt=3, max_attempts=3, error_code="PROVIDER_UNAVAILABLE", message="生成服务暂时不可用，请稍后重试。"

# 3) 不可重试业务失败（stub_outcome=job_failed）：attempt=1 直接进 dead
JOB_FAIL=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp5-fail-1","capability":"image.generate","model":"demo","params":{"prompt":"bad","stub_outcome":"job_failed"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 1
curl -s $API/jobs/$JOB_FAIL -H "pilipala-token: $TOKEN"
# → status="dead", attempt=1, error_code="JOB_FAILED", message="任务执行失败。"

# 4) 核对数据库：provider_task 有原文，且 job_step.provider_task_id 已关联
psql -d pilipala -c "SELECT job_id, job_step_id, upstream_request_id, raw_status, raw_body FROM provider_task ORDER BY created_at DESC LIMIT 5;"
psql -d pilipala -c "SELECT job_id, attempt, status, error_code, provider_task_id FROM job_step ORDER BY started_at DESC LIMIT 5;"
```
