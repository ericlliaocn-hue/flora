# 架构

本文描述 pilipala **目标架构**（M1 起）。仓库已包含 `web/`、`api/`、`worker/` 等进程；进程名、分层和时序以本文与 ADR 为准，避免按上游遗留倒推。

## 进程

| 进程 | 职责 | 不职责 |
| --- | --- | --- |
| **web** | C 端画布与托管 UI。托管模式只调用 pilipala API。静态文案走 locale。 | 不持有 Anytoken/供应商密钥；不实现钱包；不直接打上游模型。 |
| **api** | Java 21 + Spring Boot 3。认证（Sa-Token）、钱包冻结、创建 job、outbox、查询、签发 TOS 读地址、把内部错误映射成 `error_code`。 | 不在请求线程里同步跑长生成；不 fork NewAPI；不做画布渲染。 |
| **worker** | 瘦执行器。按 `job_id` 加载任务，调 Anytoken，写 `job_step` / `provider_task` / 结果键，ack 或重试或进 DLQ。 | 不读画布图；不拼节点拓扑；不 confirm/release 钱包（由 api 领域服务在状态迁移时做，或经明确的领域事件，不得散落在 worker 里复制一套账本）；不做 HTTP 面向用户的 API。 |
| **admin** | M4 才做。运维查询与配置。 | 不是第二套创作产品；不复制画布。 |

四者共享 Postgres（账本与任务）和 Redis（会话/锁/短缓存）。对象文件走火山 TOS。模型请求只经 Anytoken。

```
浏览器 (web)
    │  JSON + 会话
    ▼
pilipala api  ── Postgres / Redis / TOS 签名
    │  outbox → RabbitMQ (body = job_id)
    ▼
pilipala worker ── Anytoken ── 上游模型
    │
    ▼
写回 job_step / provider_task / TOS 对象键
```

## 分层（api）

**按领域分包**（与仓内 `art.pilipala.api.*` 一致），每个 bounded context 内再分四层；禁止跨层回流成上帝类，也**禁止**改成仓库根下全局 `controller` / `service` / `mapper` 三目录。

已落地的领域包示例：`auth`、`billing`、`job`、`admin`、`project`、`i18n`；**analytics**（WP15，先文档后代码）；另有 `storage`、`gateway` 等基础设施适配。

```
art.pilipala.api.<domain>/
  web/           # Interface：HTTP、DTO、Sa-Token 过滤器
  application/   # 用例编排（一个用例一个类，避免 TaskCenterService）
  domain/        # 状态机、不变量、仓储端口
  infra/         # MyBatis-Plus Mapper、消息/TOS 等适配（迁移完成前可能仍见 Jdbc*Repository）
```

| 层 | 职责 |
| --- | --- |
| **web**（≈ Interface） | HTTP 控制器、内部 DTO 校验。只转合同，不写业务分支。 |
| **application** | 用例。例如「提交生图」= 校验幂等 → 估价 → 冻结 → 建 job → 写 outbox。 |
| **domain** | `Job` 状态机、钱包冻结单、错误码。状态迁移函数保持纯：非法迁移直接拒绝。 |
| **infra**（Infrastructure） | Postgres + **MyBatis-Plus**（[ADR 0006](./adr/0006-mybatis-plus-persistence.md)）、Redis、RabbitMQ Transport、TOS、**唯一** Anytoken Client。application 依赖仓储端口，不在 web 层散落 SQL。 |

**跨域调用**：只调用对方领域的 **application** 用例（或明确端口）。例如 `job` 提交时通过 `billing` 的 freeze/confirm/release 用例，**禁止** `job` 直接注入 `billing` 的 Mapper 或读写他域表。

**与经典 MVC**：Controller / Service / Repository 角色仍在，但嵌在各自领域包内，不是全局三目录。

worker 只有 Application（执行一步）+ Infrastructure（队列、Anytoken、仓储）。没有 canvas 包。

### 领域与资金边界

- **auth** / `users`：登录身份与资料（含 `password_hash`）；**不**存余额。
- **billing**：钱包、`wallet_ledger`、`wallet_hold`；未来充值订单与支付渠道适配均属本域。
- **job**：任务生命周期；通过 `hold_id` 关联冻结单，不直接改余额字段。

### 领域：analytics

- **职责**：事件契约、（未来）客户端/服务端事件采集、报表口径 R1–R6；只读 Postgres 业务事实（`users` / `job*` / `wallet*`）或只追加事件行；见 [data-model/analytics.md](./data-model/analytics.md) 与 [ADR 0007](./adr/0007-analytics-events-and-reports.md)。
- **不职责**：不改钱包（freeze/confirm/release/topup）；不做广告竞价、优惠券引擎、投放后台；不把埋点逻辑塞进 `job` / `billing` 包。
- **与 auth / billing / job**：身份在 `auth`；资金与任务终态在 `billing` / `job`；analytics **消费**其数据或旁路记录意图事件，不替代账本。
- **后续域**：`growth` / `ads` / `campaign` 尚未建仓；账本不双写；活动发奖仍走 `billing` 用例，与 ADR 0001 单账本一致。事件 `props` 可预留 `campaign_id`。

## Anytoken hop

pilipala **不** 做模型聚合网关，只做一跳：

```
web → api（内部 DTO）→ worker → Anytoken → 上游
```

规则：

- 浏览器永不拿到 Anytoken token 或上游 Key。
- pilipala 用户表 / 钱包是唯一账本。Anytoken 侧若有 token 额度，那是运维成本，不映射成用户余额。
- 禁止再接 NewAPI、qfzy-api 或「临时再写一个 OpenAI 兼容代理」。供应商差异在 Anytoken 消化。
- api 给 worker 的是内部任务合同（模型名、能力、参数引用、job_id），不是把前端原始请求原样转抛。

见 [ADR 0001](./adr/0001-anytoken-not-newapi-fork.md)。

## 任务状态机

```
created → queued → running → succeeded
                ↘ failed ──(attempt < max)→ queued
                ↘ cancelled
                ↘ dead          ← 超限或不可重试，进入 DLQ
```

- MQ 消息体只有 `job_id`。worker 以数据库为准。
- `job_step` 记录每一次 attempt 的开始/结束、映射后的 `error_code`、指向 `provider_task` 的关联。
- 取消：api 标记取消意图；worker 在运行前检查；已在上游的尽量 best-effort 取消，但账本以 pilipala 状态为准。

## 计费与任务的交界

提交必须在同一事务里完成「冻得住才入队」：

1. 估价（可先简单规则，字段在 billing brief 定）。
2. `freeze` 成功才把 job 置 `queued` 并写 outbox。
3. 终态 `succeeded` → `confirm`。
4. 终态 `failed`（不再重试）、`cancelled`、`dead` → `release`。

worker 不直接改余额。它只推进 job；确认/释放由 api 侧领域服务在进入终态时执行（同进程方法或可靠领域事件，但代码上不得出现第二套钱包写入）。

## 时序：一次成功生图

```mermaid
sequenceDiagram
    actor User
    participant Web
    participant Api
    participant DB as Postgres
    participant MQ as RabbitMQ
    participant Worker
    participant AT as Anytoken
    participant TOS as Volcengine TOS

    User->>Web: 在画布提交生图
    Web->>Api: POST submit (idempotency_key, capability, params)
    Api->>DB: 幂等检查 + freeze + insert job/outbox
    Api-->>Web: job_id, status=queued
    Api->>MQ: publish {job_id}
    Web->>Api: GET query
    MQ->>Worker: {job_id}
    Worker->>DB: load job, status→running, insert job_step
    Worker->>AT: 创建生成任务
    AT-->>Worker: 上游结果或失败
    Worker->>TOS: 上传输出对象
    Worker->>DB: provider_task + result keys, status→succeeded
    Note over Api,DB: 进入 succeeded 时 confirm 冻结
    Web->>Api: GET result
    Api-->>Web: 友好状态 + TOS 读地址
```

失败时 worker 写 `provider_task` 原文、映射 `error_code`，job 回 `queued` 或进 `dead`；进入不再执行的失败终态时 release。用户查询始终不带上游原文。

## 时序：取消

```mermaid
sequenceDiagram
    participant Web
    participant Api
    participant DB as Postgres
    participant Worker

    Web->>Api: cancel(job_id)
    Api->>DB: 标记 cancel requested
    alt 尚未 running
        Api->>DB: status=cancelled, release
    else 已 running
        Worker->>DB: 看到取消意图，停止后续 attempt
        Worker->>DB: status=cancelled
        Note over Api,DB: release 冻结
    end
```

## 为什么必须拆进程

qingfeng 的教训不是「Java 不行」，而是职责粘在一起：

- 一个 TaskCenter 同时懂状态、队列、画布、生产、重试。
- worker 为了「方便」去拉创作上下文，最后变成第二个业务后端。
- 两个网关适配器并存，账本对不齐。

pilipala 用进程边界强制：web 只展示，api 只编排账本与任务，worker 只执行一跳，admin 只看和配置。

## 目录预期

| 路径 | 状态 | 说明 |
| --- | --- | --- |
| `web/` | 已存在 | 前端 fork |
| `api/` | 已存在 | Spring Boot 业务 API（领域分包 + 四层） |
| `worker/` | 已存在 | 瘦 worker |
| `docs/engineering/` | 本树 | 工程真相源 |
| `docs/content/` | 已存在 | 公开用户文档，勿改作工程站 |
