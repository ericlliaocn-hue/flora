# 开发计划

本文把 [MILESTONES.md](./MILESTONES.md) 拆成工作包 WP0–WP15，并规定 **brief → 实现 → PR → 对照 brief review** 的协作方式。

## 协作流程（所有 WP 通用）

```
写 feature brief  →  粘贴给 Minimax（或人认领）  →  开 PR  →  对照 brief 审查  →  合并
```

1. **先写 brief。** 复制 [feature-briefs/_TEMPLATE.md](./feature-briefs/_TEMPLATE.md)，放到 `feature-briefs/YYYY-MM-DD-<slug>.md`。没有 brief 的大改动不开工。
2. **Brief 是任务说明书。** 给 Minimax 时整份粘贴，不要在聊天里另起一套需求。补充需求就改 brief，再开新一轮。
3. **实现只做 brief 范围。** 禁止顺手重构、禁止扩大非范围、禁止发明与 [TECH_STACK.md](./TECH_STACK.md) 冲突的栈。
4. **PR 必须引用 brief 路径。** 描述里写：背景一句、做了什么、验收对照、刻意没做什么。
5. **Review 对照 brief，不对照聊天。** 范围外的改动一律打回。需求有价值但实现不合格时，保留需求、要求重做，不要把需求一起否掉。
6. **Admin 不插队。** C 端最小闭环（M3）之前，不把 WP11 当成日常主线。

细节清单见 [CONTRIBUTING.md](./CONTRIBUTING.md)。

## 工作包依赖

```
WP0 白标
  ↓
WP1 API/worker 骨架
  ↓
WP2 阶段1 表/字段级 Schema
  ↓
WP3 认证 ──→ WP4 Job+Outbox+RabbitMQ ──→ WP5 瘦 worker + Anytoken
                ↓                         ↓
              WP6 钱包冻结/确认/释放 ←────┘
                ↓
WP7 前端改打 flora API
  ↓
WP8 TOS 结果落地
  ↓
WP9 友好错误 + i18n_message
  ↓
WP10 C 端云端持久化
  ↓
WP11 Admin（运维/配置）
  ↓
WP12 Seedance 视频生成
  ↓
WP13 工程文档对齐（MyBatis-Plus + 领域分包；docs-first，不阻塞已合并 WP12）
  ↓
WP14 MyBatis-Plus 代码迁移（[brief](./feature-briefs/2026-10-25-wp14-mybatis-plus-code-migration.md)）
WP15 数据分析事件字典与报表口径（docs-first；可与 WP14 并行，不互相阻塞）
```

WP6 可以与 WP5 并行设计，但必须在真实出图路径接通前落地冻结。WP7 依赖内部 DTO 稳定，见 [api/gateway-contract.md](./api/gateway-contract.md)。WP13 为工程真相源修正，可与 WP14 ORM 代码迁移、WP15 分析文档分开交付。

## WP0 — flora 白标

- **里程碑**：M0
- **Brief**： [2026-10-24-wp0-white-label-flora.md](./feature-briefs/2026-10-24-wp0-white-label-flora.md)（可直接粘贴给 Minimax）
- **做什么**：用户可见品牌改为 flora；根 README 保留上游说明但顶部标明 fork 身份。
- **不做**：Java 后端、计费、重写公开文档站、删掉上游功能说明。
- **完成**：浏览器标题/主文案是 flora；工程文档入口可发现。

## WP1 — 仓库布局与 Java 骨架

- **里程碑**：M1
- **做什么**：在仓内新增业务 API（Java 21 + Spring Boot 3）与瘦 worker 模块的可启动骨架；健康检查；配置分层（本地 / 密钥不入库）。
- **建议目录**（实施时在 brief 里最终确认，不要提前造空项目）：`api/` 业务进程，`worker/` 消费进程。禁止一上来做单体上帝模块。
- **不做**：业务功能、admin、前端大改。
- **完成**：两个进程能本地启动并打到 `/health`（或等价）且不依赖画布代码。

## WP2 — 阶段 1 表/字段级 Schema

- **里程碑**：M1
- **Brief**： [2026-10-24-wp2-phase1-schema.md](./feature-briefs/2026-10-24-wp2-phase1-schema.md)
- **做什么**：在写任何 Entity / Flyway 之前，把 job / billing / gateway / errors 四个域的**表、字段、类型、索引、FK、枚举、错误码**在 Markdown 里锁死；并把原 WP2–WP10 顺延为 WP3–WP11。
- **产出**：[data-model/jobs.md](./data-model/jobs.md)、[data-model/billing.md](./data-model/billing.md)、[data-model/errors.md](./data-model/errors.md)、[data-model/README.md](./data-model/README.md)、[api/gateway-contract.md](./api/gateway-contract.md)。
- **不做**：任何 `.java` / `.sql` / Flyway / RabbitMQ / Anytoken / TOS 实现；不改前端与公开文档站；不建第二套账本。
- **完成**：实现者照抄文档即得列名与类型；`data-model` 里不再有「类型 TBD」；错误码注册表与 `i18n_message` 列级 schema 落盘；lease 等价物写明（不建 Postgres lease 表）。

## WP3 — 认证与用户

- **里程碑**：M1
- **做什么**：Sa-Token 登录/登出/会话；用户主数据；C 端请求必须带会话。`users` 表最少列见 [data-model/README.md](./data-model/README.md)。
- **不做**：社交登录全家桶、权限中台、admin 角色矩阵（内部角色可预留，UI 放到 WP11）。
- **完成**：未登录不能 `submit`；登录后能拿到稳定 `user_id`。

## WP4 — Job 状态机、Outbox、RabbitMQ

- **里程碑**：M1
- **做什么**：`job` / `job_step` / `outbox_message` / 幂等键；状态机与列级定义见 [data-model/jobs.md](./data-model/jobs.md)；事务内写 outbox，再投递；消息体只有 `job_id`。
- **状态**：`created → queued → running → succeeded`；`failed`（`attempt < max` 则回 `queued`）；`cancelled`；`dead`（DLQ）。
- **不做**：在 job 表写死 queue 名、routing key、RabbitMQ 协议字段作为领域模型。
- **完成**：单测或集成能走通成功、重试、取消、死信；换一个假 Transport 也不必改状态枚举。

## WP5 — 瘦 Worker + Anytoken

- **里程碑**：M1（假任务）/ M2（真生图）
- **做什么**：Worker 消费 `job_id`，加载 job，调 **唯一** Anytoken 适配器，写 `job_step` 与 `provider_task`。第一期可用 echo/假上游证明状态机，但适配器边界必须一次做对。
- **禁止**：`TaskCenterService` / `CanvasAgentProductionService` 式大类；worker 拉画布节点、拼工作流、确认钱包；NewAPI 第二适配器。
- **完成**：worker 模块编译依赖里看不到 canvas/creation UI；一个供应商适配器入口。

## WP6 — 预付费钱包

- **里程碑**：M2
- **做什么**：钱包余额 + 冻结单 + 流水；提交时 freeze；成功 confirm；失败/取消 release。列级与符号约定见 [data-model/billing.md](./data-model/billing.md)。
- **不做**：后付费、套餐引擎、Anytoken 侧额度对账双写。
- **完成**：余额不足不能开跑；成功不重复确认；失败不吞冻。

## WP7 — 前端 generation 走 flora API

- **里程碑**：M2
- **做什么**：托管模式下，画布生图改为 `submit / query / result` 内部合同；浏览器不再保存供应商 Key 作为正式路径。
- **不做**：删掉所有本地开源直连代码（若暂留，必须明确是非托管调试路径，且不走钱包）。
- **完成**：画布至少一条官方生图路径只打 flora API。

## WP8 — 火山 TOS

- **里程碑**：M2
- **做什么**：生成结果与必要原图/输出对象进 TOS；API 给前端短期可访问地址；worker 只传对象键，不在 MQ 里塞文件。
- **不做**：自建多云抽象、把 TOS 当数据库。
- **完成**：成功任务的结果能用 TOS 键取回。

## WP9 — 友好错误与 i18n

- **里程碑**：M2
- **做什么**：稳定 `error_code`；C 端默认响应只有友好文案；上游原文进日志 / `provider_task`；动态文案进 `i18n_message`；静态 UI 留在前端 locale。zh + en。码表见 [data-model/errors.md](./data-model/errors.md)。
- **不做**：把供应商 HTML/堆栈直接 `message.success` 出去。
- **完成**：故意触发余额不足、上游失败、取消，C 端看到码和短句，运营日志看得到原文。

## WP10 — C 端账号级持久化

- **里程碑**：M3
- **做什么**：项目 / 素材 / 生成记录与 `user_id` 绑定。字段级设计在独立 brief 里写，本 WP 只锁「必须离开纯浏览器本地」。
- **不做**：把本地格式迁移写得兼容无限旧数据（产品未上线，按新设计来，除非 brief 要求）。
- **完成**：换浏览器登录仍能看到自己的最小数据集；文档不谎称未完成的云同步。

## WP11 — Admin 运维/配置

- **里程碑**：M4
- **做什么**：用户与钱包排查、任务与死信、模型展示配置、`i18n_message` 编辑。
- **不做**：admin 里做画布、做社区、做第二套生成台。
- **完成**：不连库也能完成日常运维；IA 上与 C 端隔离。

## WP13 — 工程文档对齐（MyBatis-Plus + 领域分包）

- **里程碑**：工程真相源（跨 M；docs-first）
- **Brief**： [2026-10-25-wp13-docs-mybatis-plus-domain.md](./feature-briefs/2026-10-25-wp13-docs-mybatis-plus-domain.md)
- **做什么**：原地更新 TECH_STACK / ARCHITECTURE / ADR（含新 0006）/ data-model / briefs 索引等，冻结 MyBatis-Plus 与领域分包；澄清用户/钱包边界与前向逻辑外键策略。
- **不做**：全量 ORM 代码迁移；不改历史 WP0–WP12 brief 正文；不批量 DROP 物理 FK；不改成全局 MVC 目录。
- **完成**：文档与 ADR 0006 一致；`api/pom.xml` 不再写「不引入 ORM」为目标。

## WP14 — MyBatis-Plus 代码迁移

- **里程碑**：工程实现（跨 M）
- **Brief**：[2026-10-25-wp14-mybatis-plus-code-migration.md](./feature-briefs/2026-10-25-wp14-mybatis-plus-code-migration.md)
- **做什么**：在 `api` / `worker` 引入 `mybatis-plus-spring-boot3-starter` 3.5.17，按域 `infra` 将 `Jdbc*Repository` 替换为 Mapper + 薄适配；保留钱包 freeze/confirm/release 与 job 乐观状态显式 SQL。
- **不做**：全局 MVC 三目录；新 Flyway；批量 DROP 物理 FK；WP15 analytics 重写；HTTP/前端变更。
- **完成**：`mvn -pl api,worker -am test` 通过；main 源码无 `JdbcTemplate` 业务仓储与 `Jdbc*.java`；领域分包不变。

## WP15 — 数据分析（事件字典 + 报表口径）

- **里程碑**：增长/定价决策基线（docs-first）
- **Brief**：[2026-10-25-wp15-analytics-events-reports.md](./feature-briefs/2026-10-25-wp15-analytics-events-reports.md)
- **做什么**：ADR 0007、ARCHITECTURE analytics 域、`data-model/analytics.md` 事件字典 v0 与必出报表；索引与 DEVELOPMENT 对齐。
- **不做**：强制第三方 SDK；ads/campaign 表；WP14 ORM 迁移；默认不做 ingest 表（Phase 2 可选）。
- **完成**：文档与 ADR 0007 一致；字典含约定事件名；报表 R1–R6 口径可执行。

## 每个 WP 的 PR 期望

- 1 个 WP 可以多 PR，但每个 PR 只解决 brief 里的一截。
- 先合 brief（若 brief 尚未入仓），再合实现；或同一 PR 里 brief 与实现一起，reviewer 先读 brief。
- 破坏冻结栈、双账本、胖 worker、原始上游错误出 C 端 —— 直接不通过。

## 参考反模式（qingfeng）

实现时对照这些失败，而不是「先堆出来再拆」：

| 反模式 | 规模 | flora 对应禁令 |
| --- | --- | --- |
| `TaskCenterService` | ~3k LOC | 任务协调拆成小服务：创建、投递、状态迁移、查询各管一段 |
| `CanvasAgentProductionService` | ~5k LOC | 创作编排不进 worker，不进单一生产大类 |
| 胖 worker 拉画布/创作 | — | worker 只认 `job_id` 与 Anytoken |
| 双适配器 | — | 只有 Anytoken，不并行维护 NewAPI 适配 |

新 brief 如果把上述职责塞回一个类，审查时应要求拆 brief，而不是先合并再重构。
