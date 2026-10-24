# Feature brief：WP6 预付费钱包（freeze / confirm / release）

- 日期：2026-10-24
- 作者：flora engineering
- 对应 WP / 里程碑：WP6 / M2（预付费钱包闭环）
- 状态：已确认（待实现）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax（上方面包屑硬约束一并保留）。不要摘要。

本任务落地 **flora 预付费钱包与单账本闭环**：通过 Flyway `V4__create_wallet_domain.sql` 建立 `wallet`、`wallet_hold`、`wallet_ledger` 三张表并补齐 `job.hold_id` 物理外键；在 `api` 的 `com.flora.api.billing` 包内实现充值（`topup`）、提交任务时冻结（`freeze`）、任务成功时确认实扣（`confirm`）、任务取消或死信时解冻释放（`release`），以及 C 端余额查询接口 `GET /api/v1/wallet` 与 `WALLET_INSUFFICIENT` 错误码。**严守 ADR 0004：计费与钱包 100% 留在 `api` 模块，`worker` 模块一行不改、零钱包依赖。不做后付费、不做套餐引擎、不做支付网关回调、不做 Anytoken 双账本、不改前端 `web/`。**

## 背景

WP0–WP5 已合入 `main`（当前最新为 WP5 `1bd6817`）：

- `api` 已有认证闸门（WP3 `V1__create_users.sql`）、Job 四表与 Outbox 投递（WP4 `V2__create_job_domain.sql`）、`provider_task` 原文留痕表（WP5 `V3__create_provider_task.sql`）。
- `worker` 已实现消费 RabbitMQ `flora.jobs.execute`、抢 Redis 短锁、调唯一 `AnytokenGatewayAdapter`（默认 `stub` 模式 + `http` 模式）并推进 `job` 到 `succeeded` / 重试回 `queued` / `cancelled` / `dead`（进 DLQ）。
- WP4 与 WP5 刻意推迟了钱包：`job.hold_id` 目前恒为 `null`，提交任务不校验余额，终态不结算。
- `api` 中已预留空包 `com.flora.api.billing`（仅含 `package-info.java`）。

[DEVELOPMENT.md](../DEVELOPMENT.md) WP6：

> - **里程碑**：M2
> - **做什么**：钱包余额 + 冻结单 + 流水；提交时 freeze；成功 confirm；失败/取消 release。列级与符号约定见 [data-model/billing.md](../data-model/billing.md)。
> - **不做**：后付费、套餐引擎、Anytoken 侧额度对账双写。
> - **完成**：余额不足不能开跑；成功不重复确认；失败不吞冻。

## 必须遵守

- **ADR 0001**：单账本。用户余额只存在 flora 的 `wallet` + `wallet_ledger`；Anytoken 侧额度是公司成本，禁止在业务库建 NewAPI 风格的 `tokens` / `quotas` / `channels` 镜像表或双写配额。
- **ADR 0003**：MQ 消息体仍然只有 `job_id`（及传输层可选 `event_id`），禁止往 MQ 消息塞钱包金额或 `hold_id`。
- **ADR 0004**：Worker 必须瘦，禁止直接 confirm/release 钱包或重算用户余额。`worker` 模块在本 WP 零改动，编译依赖与包内不得出现 wallet / billing。
- **ADR 0005**：C 端只看稳定 `error_code` + 本地化友好短句；禁止抛 SQL / 异常堆栈原文。
- **TECH_STACK.md**：Java 21 + Spring Boot 3；Postgres 权威存储；金额一律 BIGINT（整数 credit），严禁 DECIMAL / FLOAT / double。
- **data-model/billing.md**：`wallet`、`wallet_hold`、`wallet_ledger` 列名、类型、约束、状态枚举（`frozen` / `confirmed` / `released`）、流水类型（`topup` / `freeze` / `confirm` / `release`）与金额符号约定的唯一照抄源。
- **data-model/jobs.md**：`job.hold_id` 外键与任务状态机对应的钱包动作。
- **data-model/errors.md**：本 WP 启用 `WALLET_INSUFFICIENT`（追加到 `ErrorCodes` 与错误文案字典）；禁止自造未登记 `error_code`。
- **api/gateway-contract.md**：公共信封（HTTP 200 + `ok` 布尔）、submit 返回 `hold_id`、冻失败返回 `WALLET_INSUFFICIENT` 且不入队。
- **CONTRIBUTING.md**：只做 brief 范围；不改 `docs/content/**` 与 `CHANGELOG.md`。

## 本 WP 拍板（已决定，禁止再猜）

| 项 | 锁定值 |
| --- | --- |
| 迁移 V4 | `api/src/main/resources/db/migration/V4__create_wallet_domain.sql`：新建 `wallet`、`wallet_hold`、`wallet_ledger` 三张表 + 对应索引，并通过 `ALTER TABLE job ADD CONSTRAINT job_hold_id_fkey FOREIGN KEY (hold_id) REFERENCES wallet_hold (id);` 补齐物理外键。严禁修改 V1 / V2 / V3 已有迁移文件 |
| 表名与列名 | 严格照抄 `data-model/billing.md`：<br>1) `wallet`：`id`、`user_id`、`available`、`frozen`、`updated_at`（共 5 列）<br>2) `wallet_hold`：`id`、`user_id`、`job_id`、`amount`、`status`、`created_at`、`finalized_at`（共 7 列，注意状态枚举是 `'frozen'` / `'confirmed'` / `'released'`，不是 `'held'`）<br>3) `wallet_ledger`：`id`、`wallet_id`、`user_id`、`type`、`amount`、`hold_id`、`job_id`、`created_at`（共 8 列，流水类型枚举 `'topup'` / `'freeze'` / `'confirm'` / `'release'`） |
| 金额与标价规则 | 金额一律 BIGINT（Java `long`），`wallet_ledger.amount` 与 `wallet_hold.amount` 恒为正整数（> 0），加减方向完全由 `type` 决定。<br>单次任务标价由配置 `flora.billing.default-job-cost=${FLORA_BILLING_DEFAULT_JOB_COST:10}` 决定（默认 10 credit，要求 > 0；抽成 `JobCostPolicy` 接口或配置类，按 capability / model 返回标价，缺省回退 default-job-cost） |
| billing.md 必选二选一（估价与实扣） | `data-model/billing.md` 要求在实现 brief 中二选一写死：本 WP 锁定「成功仍按冻结额全额 confirm（一口价标价，不拆分差额 release）」。一次 job 对应唯一一张 `wallet_hold`，confirm 金额恒等于 `hold.amount` |
| gateway-contract.md 必选拍板（冻失败行为） | `api/gateway-contract.md` 要求 WP6 写死冻失败行为：本 WP 锁定「冻失败则无 job」——余额不足（`available < cost`）时抛出 `WALLET_INSUFFICIENT`，不创建 job 行、不写 `wallet_hold`、不写 `wallet_ledger`、不写 `outbox_message`、不写 `idempotency_record`，HTTP 仍返 200 + `{"ok":false,"data":null,"error_code":"WALLET_INSUFFICIENT","message":"..."}`。用户充值后可用原 `idempotency_key` 重新提交 |
| 同键幂等重放与冻结 | 同一 `(user_id, idempotency_key)` + 相同请求体 hash 重放时，直接从已有 job 返回 `SubmitJobView(job.id, job.status, job.holdId)`，绝不二次冻结。不同请求体 hash 仍在碰钱包前抛 `IDEMPOTENCY_CONFLICT` |
| 循环外键无冲突写入顺序 | `job.hold_id -> wallet_hold(id)` 与 `wallet_hold.job_id -> job(id)` 互指（两列均可空）。在 `SubmitJob` 同一 `@Transactional` 事务内，允许以下任一等价无冲突顺序：<br>**顺序 A（推荐，内存单测也天然零残留）**：① 调 `walletService.freeze(userId, cost)`（检查 `available >= cost` 扣可用加冻结，插入 `wallet_hold(job_id=null, status='frozen')` 与 `wallet_ledger(type='freeze', job_id=null)`；若余额不足直接抛 `WALLET_INSUFFICIENT`，此时 job 尚未插入）→ ② 插入 `job(status='created', hold_id=hold.id)` 并迁为 queued → ③ 调 `walletService.bindJob(hold.id, job.id)` 回填 `wallet_hold.job_id` 与 `wallet_ledger.job_id` → ④ 写 `outbox_message` 与 `idempotency_record`（若步骤 ② 因并发同键冲突未插入，则释放或回滚本次 hold 并走 replay）；<br>**顺序 B**：先校验余额 / 插入 `job(created, hold_id=null)` → 插入 `wallet_hold(job_id=job.id)` 与 `wallet_ledger` → 更新 `job SET hold_id=hold.id, status='queued'`（若用内存假仓储跑单测，需确保 freeze 抛异常时不残留 created 状态的孤儿 job） |
| 谁触发 confirm / release（严守 ADR 0004） | worker 绝不碰钱包表。所有 confirm 与 release 均在 api 进程内通过幂等的 `WalletService` 完成，触发点有三处（共用同一套幂等方法）：<br>1) api 同步触发：`CancelJob` 将 created/queued 任务直接迁为 cancelled 时，同事务调 `release(holdId)`；`ClaimJob`（发现取消意图直接迁 cancelled）与 `RecordAttemptResult`（迁 succeeded 调 confirm，迁 cancelled/dead 调 release）同事务立即结算；<br>2) api 后台定时对账器 `WalletHoldSettlementDispatcher`：参考 `OutboxDispatcher` 模式，`@Scheduled(fixedDelayString = "${flora.billing.settle-poll-ms:200}")` 扫描 `wallet_hold.status = 'frozen'` 且关联 `job.status IN ('succeeded', 'cancelled', 'dead')` 的记录（单批默认 `${flora.billing.settle-batch-size:50}`），对 succeeded 调 `confirm(holdId)`，对 cancelled / dead 调 `release(holdId)`；<br>3) api 读接口兜底触发（消除轮询窗口抖动）：`GetJob`、`GetJobResult`、`CancelJob`（已终态分支）若发现当前 job 已处于终态且 `holdId != null`，或 `GetWallet` 查询当前用户钱包前，顺手调用一次幂等结算，保证 worker 刚写完终态后客户端立刻查询 job 或 wallet 时，账本已 100% 实时收敛 |
| 重试不释放、不重冻 | 当任务第 1、2 次 attempt 失败并按 `attempt < max_attempts` 迁回 queued 时，job 处于非终态 queued，其 `wallet_hold` 保持 `status = 'frozen'`（既不 release 也不二次 freeze）。只有最终落 succeeded 才 confirm，最终落 dead 或 cancelled 才 release |
| confirm / release 幂等与并发安全 | 在 Postgres 中以原子条件更新 `UPDATE wallet_hold SET status = 'confirmed'`（或 `'released'`）, `finalized_at = now() WHERE id = ? AND status = 'frozen' RETURNING ...` 作为闸门：<br>- 若更新行数为 1：同事务更新 wallet（confirm：`frozen = frozen - amount`；release：`available = available + amount, frozen = frozen - amount`）并插入 `wallet_ledger`（`type = 'confirm'` 或 `'release'`）；<br>- 若更新行数为 0：查当前 `wallet_hold.status`——若已是目标终态（confirmed 重放 confirm，或 released 重放 release），幂等静默返回，不改 wallet、不双写 `wallet_ledger`；若已是相反终态（如已 released 却调 confirm，或已 confirmed 却调 release），打 warn 日志并抛非法状态异常 |
| 初始余额与本地充值路径 | 1) 配置项 `flora.billing.initial-credits=${FLORA_BILLING_INITIAL_CREDITS:0}`（默认 0）：用户注册（`RegisterUser`）或首次访问钱包时确保存在 wallet 行（`available=0, frozen=0`）；若 `initial-credits > 0`，必须经由 topup 路径同时写入 `wallet(available = initialCredits)` 与 `wallet_ledger(type='topup', amount=initialCredits)`（严禁只改 `wallet.available` 不写流水）。<br>2) 应用层提供 `TopUpWallet` 用例（`topup(UUID userId, long amount)`：校验 `amount > 0`，加 `wallet.available` 并写 `wallet_ledger(type='topup')`），供单测/集成测试与未来 WP11 Admin 调用。<br>3) 不暴露 C 端自助免费充值 HTTP 接口（防生产越权刷钱）；本地 curl 联调使用 `runbooks/local-dev.md` 中提供的单条 psql 原子充值 SQL（同时写 wallet + `wallet_ledger(type='topup')`）或环境变量 `FLORA_BILLING_INITIAL_CREDITS` |
| C 端钱包查询接口 | 新增 `GET /api/v1/wallet`（受 WP3 `AuthPathPolicy` 保护，无 token → UNAUTHORIZED）：返回当前登录用户的钱包快照 `{"ok":true,"data":{"user_id":"...","available":90,"frozen":10,"updated_at":"..."},"error_code":null,"message":null}`。若用户尚无 wallet 行，惰性初始化一行 `available=0, frozen=0` 后返回 |
| `WALLET_INSUFFICIENT` 文案 | `ErrorCodes` 新增 `WALLET_INSUFFICIENT = "WALLET_INSUFFICIENT"`；错误文案字典新增：<br>- zh：「账户余额不足，请先充值。」<br>- en：「Insufficient wallet balance. Please top up first.」 |

## 本 WP 不拍板（禁止发明，留给后续 brief）

- 第三方支付渠道（微信 / 支付宝 / Stripe）回调与充值订单表（非 stage-1 M2 范围）。
- 套餐 / 订阅 / 优惠券 / 阶梯折扣 / 后付费透支引擎。
- C 端自助充值 HTTP 接口（M4 / WP11 Admin 才提供受控加额接口）。
- 火山 TOS 上传与签名 URL（WP8）。
- 前端 `web/` 改打 flora API 与余额展示（WP7）。
- `i18n_message` 数据库表（WP9）。
- 在 worker 中加入任何钱包代码或依赖。

## 目标

1. Flyway `V4__create_wallet_domain.sql` 建 `wallet` / `wallet_hold` / `wallet_ledger` 三张表并给 `job.hold_id` 补上物理外键，表名、列名、类型、约束与 `data-model/billing.md` 逐字一致。
2. 在 `com.flora.api.billing` 落地完整预付费账本服务：topup、freeze、confirm、release、getOrCreateWallet，保证每次余额或冻结额变动必有对应 `wallet_ledger` 流水，且 `available >= 0`、`frozen >= 0`。
3. 改造 `SubmitJob`：同事务内完成 freeze（默认每单 10 credit，可通过 `FLORA_BILLING_DEFAULT_JOB_COST` 配置）+ 写 `job(queued, hold_id)` + 写 `outbox_message` + 写 `idempotency_record`；余额不足抛 `WALLET_INSUFFICIENT` 且不落库任何 job / outbox / hold；同键同 body 重放返回原 `job_id` 与原 `hold_id`，不二次冻结。
4. 打通终态结算闭环（api 同步触发 + `WalletHoldSettlementDispatcher` 定时扫描 + 读接口幂等兜底）：
   - 任务 succeeded → `wallet_hold` 变为 confirmed，`wallet.frozen` 扣减，写 `wallet_ledger(type='confirm')`；
   - 任务 cancelled（含排队中直接取消、运行中取消）或 dead（含不可重试直接 dead、可重试 3 次用尽后 dead）→ `wallet_hold` 变为 released，`wallet.frozen` 退回 `wallet.available`，写 `wallet_ledger(type='release')`；
   - 重放 confirm / release 完全幂等。
5. 新增 `GET /api/v1/wallet` 查询当前登录用户余额（`user_id`、`available`、`frozen`、`updated_at`）。
6. `worker` 模块保持 0 改动，双进程（api + worker）联调下：无余额拒绝提交、充值后提交成功并自动 confirm、失败自动 release、取消自动 release。
7. 更新 `docs/engineering/api/gateway-contract.md`、`docs/engineering/data-model/README.md`、`docs/engineering/feature-briefs/README.md`、`docs/engineering/runbooks/local-dev.md`。

## 范围

### 允许新增 / 修改

| 路径 | 动作 |
| --- | --- |
| `docs/engineering/feature-briefs/2026-10-24-wp6-prepaid-wallet.md` | 新增（本文件全文落入仓） |
| `docs/engineering/feature-briefs/README.md` | 索引表新增 WP6 行；将 WP5 状态更新为「已实现，待验收」 |
| `docs/engineering/data-model/README.md` | 将 `wallet` / `wallet_hold` / `wallet_ledger` 标为「WP6 已落地」（不改列定义） |
| `docs/engineering/api/gateway-contract.md` | 将 Submit 响应 `status` 旁注「（WP6 写死一种）」更新为已锁定的「冻失败则无 job，返 WALLET_INSUFFICIENT」；新增 Wallet（`GET /api/v1/wallet`）小节 |
| `docs/engineering/runbooks/local-dev.md` | 新增 WP6 环境变量、本地 psql 充值脚本与端到端钱包 curl 验收步骤；更新诚实对照表 |
| `api/src/main/resources/db/migration/V4__create_wallet_domain.sql` | 新增 `wallet`、`wallet_hold`、`wallet_ledger` 表、索引与 `job.hold_id` 外键 |
| `api/src/main/resources/application.yml` | 新增 `flora.billing.*` 配置段（default-job-cost、initial-credits、settle-poll-ms、settle-batch-size） |
| `api/src/main/java/com/flora/api/common/ErrorCodes.java` | 追加 `WALLET_INSUFFICIENT` |
| `api/src/main/java/com/flora/api/job/infra/JobErrorMessages.java` | 追加 `WALLET_INSUFFICIENT` 的 zh / en 短句 |
| `api/src/main/java/com/flora/api/job/infra/JobException.java` | 新增 `walletInsufficient()` 工厂方法（或由 `BillingException` 统一映射为同一信封） |
| `api/src/main/java/com/flora/api/billing/**` | 新增钱包领域模型、应用服务（`WalletService` / `TopUpWallet` / `GetWallet` / `WalletHoldSettlementTick`）、JDBC 仓储、`WalletHoldSettlementDispatcher`、`WalletController` |
| `api/src/main/java/com/flora/api/auth/application/RegisterUser.java` | 注册成功后初始化用户钱包（若 `initial-credits > 0` 则走 topup 写入初始额度与流水） |
| `api/src/main/java/com/flora/api/job/infra/JobRepository.java` & `JdbcJobRepository.java` | 支持在创建/入队时写入或绑定 `hold_id` |
| `api/src/main/java/com/flora/api/job/application/SubmitJob.java` | 接入 freeze，返回非空 `hold_id`；冻失败抛 `WALLET_INSUFFICIENT` |
| `api/src/main/java/com/flora/api/job/application/CancelJob.java` | 直接取消 created/queued 任务时同事务 release；已终态时幂等兜底结算 |
| `api/src/main/java/com/flora/api/job/application/ClaimJob.java` & `RecordAttemptResult.java` | 迁入终态（succeeded / cancelled / dead）时同事务调用 confirm / release |
| `api/src/main/java/com/flora/api/job/application/GetJob.java` & `GetJobResult.java` | 若查到的任务已处于终态且 `hold_id != null`，顺手触发幂等结算兜底 |
| `api/src/test/java/com/flora/api/**` | 新增 billing 单元测试（freeze/confirm/release/topup/幂等/并发边界/控制器切片），更新并扩充 `JobLifecycleTest` 与 `JobControllerSliceTest` |

### 建议 `com.flora.api.billing` 包结构（职责拆开，禁止上帝类）

```
com.flora.api.billing
  package-info.java
  domain/
    Wallet.java                       （id, userId, available, frozen, updatedAt）
    WalletHold.java                   （id, userId, jobId, amount, status, createdAt, finalizedAt）
    WalletHoldStatus.java             （枚举：FROZEN("frozen"), CONFIRMED("confirmed"), RELEASED("released")）
    WalletLedgerEntry.java            （id, walletId, userId, type, amount, holdId, jobId, createdAt）
    WalletLedgerType.java             （枚举：TOPUP("topup"), FREEZE("freeze"), CONFIRM("confirm"), RELEASE("release")）
  application/
    WalletService.java                （核心账本事务：ensureWallet / topup / freeze / bindJob / confirm / release / settleForJob）
    GetWallet.java                    （先触发该用户未决终态 hold 结算，再返回 WalletView）
    WalletView.java                   （user_id, available, frozen, updated_at）
    JobCostPolicy.java                （计算单次任务冻结额，默认读 flora.billing.default-job-cost）
    WalletHoldSettlementTick.java     （扫描 status='frozen' 且 job 已终态的 hold，逐笔调 confirm 或 release）
  infra/
    BillingProperties.java            （绑定 flora.billing.*：defaultJobCost=10, initialCredits=0, settlePollMs=200, settleBatchSize=50）
    WalletRepository.java / JdbcWalletRepository.java
    WalletHoldRepository.java / JdbcWalletHoldRepository.java
    WalletLedgerRepository.java / JdbcWalletLedgerRepository.java
    WalletHoldSettlementDispatcher.java （@Scheduled 定时调 WalletHoldSettlementTick）
  web/
    WalletController.java             （GET /api/v1/wallet）
```

仓储同样定义成接口（`WalletRepository`、`WalletHoldRepository`、`WalletLedgerRepository`），测试提供 `InMemoryWalletStore`（与 `InMemoryJobStore` 配合），无需外部 DB 即可在单测里跑通完整 `submit(freeze) → claim → retry → succeed(confirm) / cancel(release) / dead(release)` 全链路。

## 非范围

- 不改 `worker/` 下任何文件（`worker/pom.xml` 与 `worker/src/**` 一行不动）。
- 不做后付费、透支、负数余额、套餐、折扣、退款到银行卡。
- 不做 C 端自助充值接口（无 `POST /api/v1/wallet/topup` 公开端点）。
- 不做 Anytoken 额度同步或 NewAPI 表。
- 不做 TOS 上传与签名 URL（WP8）。
- 不做 `i18n_message` 表（WP9）。
- 不改 `web/` 前端、`docs/content/**`、`CHANGELOG.md`。

## 数据表字段（强制照抄 `data-model/billing.md`）

本 WP 新增 `api/src/main/resources/db/migration/V4__create_wallet_domain.sql`，严禁修改 V1 / V2 / V3。

### 1. `wallet`

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK DEFAULT `gen_random_uuid()` | |
| `user_id` | UUID | NO | UNIQUE；FK → `users(id)` | 一用户一钱包 |
| `available` | BIGINT | NO | DEFAULT 0；`CHECK (available >= 0)` | 可用 credit |
| `frozen` | BIGINT | NO | DEFAULT 0；`CHECK (frozen >= 0)` | 已冻未确认 credit |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT `now()` | |

### 2. `wallet_hold`（冻结单）

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK DEFAULT `gen_random_uuid()` | 亦即 `job.hold_id` |
| `user_id` | UUID | NO | FK → `users(id)`；IDX `(user_id, created_at)` | 冗余便于对账 |
| `job_id` | UUID | YES | UNIQUE；FK → `job(id)` | 与 job 同事务创建/绑定 |
| `amount` | BIGINT | NO | `CHECK (amount > 0)` | 冻结额（正整数） |
| `status` | TEXT | NO | `CHECK (status IN ('frozen', 'confirmed', 'released'))` | 终态只进一次 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT `now()` | |
| `finalized_at` | TIMESTAMPTZ | YES | | `confirm` / `release` 时间 |

建议额外加一个部分索引加速未决冻结单扫描：

```sql
CREATE INDEX wallet_hold_frozen_idx ON wallet_hold (created_at) WHERE status = 'frozen';
```

### 3. `wallet_ledger`（流水）

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK DEFAULT `gen_random_uuid()` | |
| `wallet_id` | UUID | NO | FK → `wallet(id)`；IDX `(wallet_id, created_at)` | |
| `user_id` | UUID | NO | FK → `users(id)` | 冗余便于对账 |
| `type` | TEXT | NO | `CHECK (type IN ('topup', 'freeze', 'confirm', 'release'))` | 流水类型 |
| `amount` | BIGINT | NO | `CHECK (amount != 0)`（代码恒写 > 0 正整数） | 方向由 type 决定 |
| `hold_id` | UUID | YES | FK → `wallet_hold(id)` | 关联冻结单（topup 时为 NULL） |
| `job_id` | UUID | YES | FK → `job(id)` | 关联任务（topup 时为 NULL） |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT `now()` | |

### 4. `job.hold_id` 外键补齐（在 V4 末尾执行）

```sql
ALTER TABLE job
    ADD CONSTRAINT job_hold_id_fkey
    FOREIGN KEY (hold_id) REFERENCES wallet_hold (id);
```

### 5. 金额符号与账本不变量（锁定）

| `wallet_ledger.type` | `wallet.available` 变化 | `wallet.frozen` 变化 | `wallet_hold.status` 变化 | `wallet_ledger.amount` |
| --- | --- | --- | --- | --- |
| `topup` | `+amount` | 不变 | — | `+amount`（正整数） |
| `freeze` | `-amount` | `+amount` | 新建 `'frozen'` | `+amount`（正整数） |
| `confirm` | 不变 | `-amount` | `'frozen'` → `'confirmed'`（写 `finalized_at`） | `+amount`（正整数） |
| `release` | `+amount` | `-amount` | `'frozen'` → `'released'`（写 `finalized_at`） | `+amount`（正整数） |

核心不变量（测试必须断言）：

- 任意时刻 `wallet.available >= 0` 且 `wallet.frozen >= 0`。
- 任意用户：`wallet.available == Σ(topup) - Σ(freeze) + Σ(release)`。
- 任意用户：`wallet.frozen == Σ(freeze) - Σ(confirm) - Σ(release) == Σ(status='frozen' 的 wallet_hold.amount)`。
- 每张 `wallet_hold` 最多对应 1 条 freeze 流水，且最多对应 1 条 confirm 或 release 流水（绝不同时出现，也绝不重复出现）。

## API 合同

继承 WP3/WP4 的统一信封（HTTP 状态码恒为 200，成功 `ok=true`，失败 `ok=false` + `error_code` + `message`）。

### 1. `GET /api/v1/wallet`（新增，需登录）

鉴权：由 `AuthPathPolicy` 覆盖（`/api/v1/**`），无有效 `flora-token` 返回 UNAUTHORIZED。

响应（200 OK）：

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

### 2. `POST /api/v1/jobs/submit`（更新冻结行为）

余额充足时（200 OK）：`hold_id` 不再为 null，返回本次新建（或幂等重放）的 `wallet_hold.id`：

```json
{
  "ok": true,
  "data": {
    "job_id": "222ffe85-5f38-4abb-8694-4ea7702abe85",
    "status": "queued",
    "hold_id": "7c9e6679-7425-40de-944b-e07fc1f90ae7"
  },
  "error_code": null,
  "message": null
}
```

余额不足时（200 OK，业务失败）：不建 job、不写 `outbox_message`、不写 `idempotency_record`、不写 `wallet_hold`：

```json
{
  "ok": false,
  "data": null,
  "error_code": "WALLET_INSUFFICIENT",
  "message": "账户余额不足，请先充值。"
}
```

（当请求头带 `Accept-Language: en` 时，`message` 为 `Insufficient wallet balance. Please top up first.`）

## 状态机与钱包协同流程（锁定）

1. `SubmitJob`（@Transactional in api）：

   ```text
   ├─ 查 idempotency_record：
   │    ├─ 已存在且 hash 一致 → 直接返回原 (job_id, status, hold_id)，不二次 freeze
   │    └─ 已存在但 hash 不同 → 抛 IDEMPOTENCY_CONFLICT
   ├─ 计算本次 cost = jobCostPolicy.costOf(capability, model)（默认 10）
   ├─ 冻结钱包：
   │    ├─ 确保用户存在 wallet 行
   │    ├─ UPDATE wallet SET available = available - cost, frozen = frozen + cost
   │    │  WHERE user_id = ? AND available >= cost
   │    │  （若更新 0 行 → 抛 WALLET_INSUFFICIENT，事务回滚，无 job 落库）
   │    ├─ INSERT wallet_hold (status = 'frozen', amount = cost)
   │    └─ INSERT wallet_ledger (type = 'freeze', amount = cost)
   ├─ 写入 job (status = 'queued', hold_id = hold.id) 并绑定 hold.job_id & ledger.job_id
   └─ 写入 outbox_message (pending) + idempotency_record
   ```

2. 任务执行中 / 失败重试中：

   ```text
   ├─ queued → running → failed (attempt < max_attempts) → queued
   └─ 期间 wallet_hold 始终保持 status = 'frozen'，不重复 freeze、不提前 release
   ```

3. 任务进入终态（succeeded / cancelled / dead）后的结算（全部在 api 侧完成，幂等）：

   ```text
   ├─ 路径 A（queued/created 任务在 api 被 CancelJob 直接取消）：
   │    └─ CancelJob 同事务调 walletService.release(holdId)
   ├─ 路径 B（api 内 ClaimJob / RecordAttemptResult 驱动到终态）：
   │    └─ 同事务按终态调 confirm(holdId) 或 release(holdId)
   └─ 路径 C（worker 进程在 DB 将 job 推进到 succeeded / cancelled / dead）：
        ├─ api 的 WalletHoldSettlementDispatcher (@Scheduled 每 200ms) 扫描
        │  status='frozen' 且 job.status IN ('succeeded','cancelled','dead') 的 hold，
        │  逐笔在独立短事务中调 confirm(holdId) 或 release(holdId)
        └─ api 的 GetJob / GetJobResult / GetWallet 在读取时若发现终态未结算 hold，
           顺手触发同款幂等结算（确保 curl / 前端零延迟看到最新余额）
   ```

## 配置项（`api/src/main/resources/application.yml`）

```yaml
flora:
  billing:
    # 单次任务默认冻结 credit 数（正整数）。
    default-job-cost: ${FLORA_BILLING_DEFAULT_JOB_COST:10}
    # 新注册用户初始赠送 credit 数（默认 0；若 > 0 则注册时自动记一笔 topup 流水）。
    initial-credits: ${FLORA_BILLING_INITIAL_CREDITS:0}
    # 后台扫描 worker 已完成任务并自动 confirm/release 冻结单的间隔（毫秒）与批大小。
    settle-poll-ms: ${FLORA_BILLING_SETTLE_POLL_MS:200}
    settle-batch-size: ${FLORA_BILLING_SETTLE_BATCH_SIZE:50}
```

## 验收

- [ ] 存在 `docs/engineering/feature-briefs/2026-10-24-wp6-prepaid-wallet.md`。
- [ ] `docs/engineering/feature-briefs/README.md` 有 WP6 行，WP5 状态更新为「已实现，待验收」。
- [ ] `docs/engineering/data-model/README.md` 将 `wallet` / `wallet_hold` / `wallet_ledger` 更新为「WP6 已落地」。
- [ ] `docs/engineering/api/gateway-contract.md` 写明冻失败无 job 且返 `WALLET_INSUFFICIENT`，并补充 `GET /api/v1/wallet` 合同。
- [ ] Flyway `V4__create_wallet_domain.sql` 建 `wallet` / `wallet_hold` / `wallet_ledger` 三表并补齐 `job.hold_id` 外键；表名、列名、CHECK 约束与 `data-model/billing.md` 逐字一致；V1/V2/V3 未被修改。
- [ ] `ErrorCodes` 与 `JobErrorMessages` 包含 `WALLET_INSUFFICIENT`（含 zh / en 友好文案）。
- [ ] `GET /api/v1/wallet` 需登录；无 token 返回 UNAUTHORIZED（HTTP 200 + ok=false）；登录后返回 `user_id`、`available`、`frozen`、`updated_at`。
- [ ] 余额不足（如新用户 available=0）调用 `POST /api/v1/jobs/submit` → 返回 `ok=false`, `error_code="WALLET_INSUFFICIENT"`，且 `job`、`wallet_hold`、`wallet_ledger`、`outbox_message`、`idempotency_record` 均 0 新增行。
- [ ] 充值（topup）后 `wallet.available` 增加，且 `wallet_ledger` 必有一条 `type='topup'` 正整数记录；随后可用之前因余额不足失败的同一个 `idempotency_key` 成功提交。
- [ ] 余额充足时 submit：`job.status='queued'`，响应与 DB 的 `job.hold_id` 非空；`wallet.available` 减 cost、`wallet.frozen` 加 cost；`wallet_hold.status='frozen'`；`wallet_ledger` 有一条 `type='freeze'` 记录。
- [ ] 同 `idempotency_key` + 同 body 重放：返回同一 `job_id` 与同一 `hold_id`，不二次扣减 `available`、不新增 `wallet_hold` 或 `wallet_ledger`。
- [ ] 任务成功（succeeded）：`wallet_hold.status` 变为 `'confirmed'`、`finalized_at` 非空；`wallet.frozen` 减 cost、`wallet.available` 不变；`wallet_ledger` 新增一条 `type='confirm'` 记录；重复调用 confirm 完全幂等（不二次扣减、不写第二条 confirm 流水）。
- [ ] 任务失败重试中（第 1、2 次 attempt 失败回 queued）：`wallet_hold.status` 保持 `'frozen'`，不重冻也不释放；第 3 次 attempt 用尽落 dead（或不可重试直接落 dead）后：`wallet_hold.status` 变为 `'released'`，`wallet.frozen` 减 cost、`wallet.available` 加回 cost，`wallet_ledger` 新增一条 `type='release'` 记录。
- [ ] 任务取消（无论是 queued 直接取消，还是 running 期间取消后由 worker 收尾为 cancelled）：`wallet_hold.status` 变为 `'released'`，冻结额全额退回 available，`wallet_ledger` 新增一条 `type='release'` 记录；重复调用 release 完全幂等。
- [ ] `worker/` 目录零文件改动，worker 编译依赖与包内无 wallet / billing。
- [ ] `./mvnw test` 全部单测通过（覆盖账本不变量、幂等重放、非法终态互转拒绝、与 Job 生命周期联动、`WalletController` 切片）。
- [ ] `docs/engineering/runbooks/local-dev.md` 更新 WP6 充值 SQL 与双进程端到端 curl 验证步骤。
- [ ] 分支 `feat/wp6-prepaid-wallet`；PR base = `main`；无真实密钥入库；不要自行 merge。

## 非目标

- 不接真实支付网关（微信/支付宝/Stripe）。
- 不做套餐包月、透支额度、优惠券或阶梯计价。
- 不在 Anytoken 侧维护用户余额。
- 不把「钱包跑通」宣传成「线上托管生图已具备对象存储」（TOS 在 WP8）。
- 不做前端 `web/` 改动、不做 Admin。

## 给 Minimax 的额外约束

- **先读再写**：`DEVELOPMENT.md`（WP6）、`TECH_STACK.md`、`data-model/billing.md`、`data-model/jobs.md`、`data-model/errors.md`、`api/gateway-contract.md`、ADR 0001 / 0003 / 0004 / 0005、本 brief、现有 `api/src/main/java/com/flora/api/**`、V1–V3 迁移、`runbooks/local-dev.md`。
- **严守模块边界**：`worker/` 目录不要碰（0 行改动）。所有钱包表读写都在 api 进程。
- **注意现有单测兼容**：`JobLifecycleTest` 之前假设无钱包（`holdId == null`）。接入钱包后，请在 `JobLifecycleTest` 初始化时通过 `InMemoryWalletStore` 给测试用户充值（如 100 credit），将原断言更新为 `assertThat(view.holdId()).isNotBlank()`，并补充余额不足、成功 confirm、重试保持 frozen、dead/cancel 自动 release 的断言。
- **全部测试必须绿**：`./mvnw test` 同时跑通 api 与 worker 全部单测。
- **做完用简短中文汇报**：改了哪些模块、V4 建了哪些表和约束、本地双进程 + curl 实测结果（无余额被拒 / 充值 / 成功 confirm / dead 自动 release / cancel 自动 release / 账本对账 SQL）、故意没做哪些（支付渠道 / 后付费 / Anytoken 双写 / TOS / 前端 / Admin）。

## 留给实现者的选择（最多拍这几项，PR 写明选了哪样）

1. `SubmitJob` 中 job 与 `wallet_hold` 的同事务写入顺序：
   - **方式 A**：先 freeze（插 `wallet_hold(job_id=null)` + `wallet_ledger`）→ 再插 `job(hold_id=hold.id, status='queued')` → 再回填 `wallet_hold.job_id` 与 `wallet_ledger.job_id`；
   - **方式 B**：先校验/扣减钱包并插 `job(status='created', hold_id=null)` → 再插 `wallet_hold(job_id=job.id)` + `wallet_ledger(job_id=job.id)` → 再更新 `job(hold_id=hold.id, status='queued')`（内存测试仓储需保证 freeze 失败不残留 created 行）。

   无论选哪种，必须满足外键约束，且 `WALLET_INSUFFICIENT` 时 `job` / `wallet_hold` / `wallet_ledger` / `outbox_message` / `idempotency_record` 均为 0 行。

2. `BillingException` 还是复用 `JobException`：可单独定义 `BillingException` 并在 `@RestControllerAdvice` 映射为统一信封，或在 `JobException` 上加 `walletInsufficient()`。对外 JSON 信封格式完全一致即可。

3. 不要再自行扩大其它开放问题。

## 分支 / Commit / PR 指令

```bash
git fetch origin
git checkout main
git pull origin main
git checkout -b feat/wp6-prepaid-wallet
# 1) 落入 brief + 更新 feature-briefs/README.md、data-model/README.md、api/gateway-contract.md
# 2) api: V4 迁移 + com.flora.api.billing + SubmitJob/CancelJob/RecordAttemptResult/GetJob 联动 + GET /api/v1/wallet + 单测
# 3) 更新 runbooks/local-dev.md
git add docs/engineering api
git commit -m "WP6: 预付费钱包 freeze/confirm/release 与单账本流水"
git push -u origin feat/wp6-prepaid-wallet
```

开 PR → main：

- 标题：`WP6: 预付费钱包 freeze / confirm / release`
- 正文必须含：brief 路径 `docs/engineering/feature-briefs/2026-10-24-wp6-prepaid-wallet.md`、做了什么、验收自勾、故意没做（无后付费 / 无支付渠道 / 无 Anytoken 双账本 / worker 零改动）、本地双进程 curl + psql 账本实测记录、实现者二选一结论。
- **不要自行 merge。**

## 环境变量提示（写入 runbook，勿填真实值）

```bash
# 继承 WP3 / WP4 / WP5 的基础变量…
export FLORA_BILLING_DEFAULT_JOB_COST=10
export FLORA_BILLING_INITIAL_CREDITS=0
export FLORA_BILLING_SETTLE_POLL_MS=200
export FLORA_BILLING_SETTLE_BATCH_SIZE=50
```

## curl 与 psql 端到端验收草稿（同时启动 api 与 worker，写进 runbook）

```bash
API=http://127.0.0.1:8080/api/v1
# 1) 注册新用户 bob（默认初始余额为 0）
TOKEN=$(curl -s -X POST $API/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"password1","locale":"zh"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
# 2) 查钱包：available=0, frozen=0
curl -s $API/wallet -H "flora-token: $TOKEN"
# → {"ok":true,"data":{"user_id":"...","available":0,"frozen":0,"updated_at":"..."},"error_code":null,"message":null}
# 3) 余额不足时 submit → WALLET_INSUFFICIENT，且不入队
curl -s -X POST $API/jobs/submit -H "flora-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp6-ok-1","capability":"image.generate","model":"demo","params":{"prompt":"sunflower"}}'
# → {"ok":false,"data":null,"error_code":"WALLET_INSUFFICIENT","message":"账户余额不足，请先充值。"}
# 4) 运维 SQL 充值 100 credit（原子更新 wallet 并记一笔 topup 流水）
psql -d flora -c "
WITH w AS (
    INSERT INTO wallet (user_id, available, frozen)
    SELECT id, 100, 0 FROM users WHERE username = 'bob'
    ON CONFLICT (user_id) DO UPDATE
        SET available = wallet.available + 100,
            updated_at = now()
    RETURNING id, user_id
)
INSERT INTO wallet_ledger (wallet_id, user_id, type, amount)
SELECT id, user_id, 'topup', 100 FROM w;
"
curl -s $API/wallet -H "flora-token: $TOKEN"
# → available=100, frozen=0
# 5) 用刚才被拒的同一个 idempotency_key 重新提交（正常生图）：
#    提交瞬间 freeze 10 → worker 消费成功 → 自动 confirm 10
JOB_OK=$(curl -s -X POST $API/jobs/submit -H "flora-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp6-ok-1","capability":"image.generate","model":"demo","params":{"prompt":"sunflower"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 1
curl -s $API/jobs/$JOB_OK -H "flora-token: $TOKEN"
# → status="succeeded"
curl -s $API/wallet -H "flora-token: $TOKEN"
# → available=90, frozen=0（10 credit 已由 frozen 转为 confirmed 实扣）
# 6) 不可重试失败（stub_outcome=job_failed）：freeze 10 → dead → 自动 release 10
JOB_FAIL=$(curl -s -X POST $API/jobs/submit -H "flora-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp6-fail-1","capability":"image.generate","model":"demo","params":{"prompt":"bad","stub_outcome":"job_failed"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 1
curl -s $API/jobs/$JOB_FAIL -H "flora-token: $TOKEN"
# → status="dead", error_code="JOB_FAILED"
curl -s $API/wallet -H "flora-token: $TOKEN"
# → available=90, frozen=0（失败不吞冻，10 credit 已退回 available）
# 7) 运行中取消（stub_delay_ms=3000）：freeze 10 → running 中点取消 → cancelled → 自动 release 10
JOB_CANCEL=$(curl -s -X POST $API/jobs/submit -H "flora-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp6-cancel-1","capability":"image.generate","model":"demo","params":{"prompt":"slow","stub_delay_ms":3000}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
curl -s $API/wallet -H "flora-token: $TOKEN"
# → 运行中可观察到 available=80, frozen=10
sleep 1
curl -s -X POST $API/jobs/$JOB_CANCEL/cancel -H "flora-token: $TOKEN"
sleep 3
curl -s $API/jobs/$JOB_CANCEL -H "flora-token: $TOKEN"
# → status="cancelled", error_code="JOB_CANCELLED"
curl -s $API/wallet -H "flora-token: $TOKEN"
# → available=90, frozen=0（取消后 10 credit 已退回 available）
# 8) 核对数据库三张表与流水对账
psql -d flora -c "SELECT available, frozen FROM wallet WHERE user_id = (SELECT id FROM users WHERE username = 'bob');"
psql -d flora -c "SELECT id, job_id, amount, status, finalized_at FROM wallet_hold WHERE user_id = (SELECT id FROM users WHERE username = 'bob') ORDER BY created_at;"
psql -d flora -c "SELECT type, amount, hold_id, job_id, created_at FROM wallet_ledger WHERE user_id = (SELECT id FROM users WHERE username = 'bob') ORDER BY created_at;"
```
