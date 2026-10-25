# Feature brief：WP15 数据分析 — 事件字典 v0 + 必出报表 + analytics 域（docs-first）

- 日期：2026-10-25
- 作者：flora engineering
- 对应 WP / 里程碑：WP15 / 增长与定价决策基线（docs-first；采集入库为可选 Phase 2 / WP15b）
- 状态：已确认（待实现）
- 建议分支：`feat/wp15-analytics-events-reports`
- 预计 PR 数：1（**以文档为主**；Phase 1 **禁止**强制落地 `analytics_event` 表与 ingest API，除非实施者在 PR 中明确勾选「含可选 Phase 2」且另过验收）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax。不要摘要。实现者只改本 brief 点名的路径；验收以「仓内工程文档含 analytics 域、事件字典、报表目录与 ADR 0007」为准。

---

## 0. 给实施者（Minimax）的先读指令

动工前**必须**按顺序读完：

1. `AGENTS.md`
2. `docs/engineering/TECH_STACK.md`
3. `docs/engineering/ARCHITECTURE.md`
4. `docs/engineering/adr/README.md`
5. `docs/engineering/adr/0001-anytoken-not-newapi-fork.md`（账本唯一性：分析不得另建额度）
6. `docs/engineering/adr/0004-thin-worker-no-god-classes.md`（worker 不碰钱包）
7. `docs/engineering/data-model/README.md`
8. `docs/engineering/data-model/billing.md`
9. `docs/engineering/data-model/jobs.md`
10. `docs/engineering/feature-briefs/README.md`
11. `docs/engineering/DEVELOPMENT.md`（确认 **WP14** 预留给 MyBatis-Plus **代码**迁移；本任务是 **WP15**，**不得**占用 WP14 编号）

**本 WP 的硬约束：**

- **改历史文档本身**（ARCHITECTURE / TECH_STACK 如需 / ADR / 新建 data-model/analytics.md / feature-briefs 索引 / DEVELOPMENT 等），**不要**另建「修订说明 / changelog 页」当唯一交付。
- **WP 编号**：**WP14 = MyBatis-Plus 代码迁移（另 brief，本 PR 不做）**；本任务固定为 **WP15**。禁止把本 PR 标成 WP14，禁止「顺手」做 ORM 迁移。
- **Phase 1（默认必须完成）** = 文档真相源：ADR 0007 + 事件字典 v0 + 报表目录 + 架构域说明。
- **Phase 2（可选，默认可不做）** = `analytics_event` 表 + 最小 ingest API；若做，必须单独在 PR 描述勾选，且仍遵守「analytics 不改钱包」。
- **不要**建 ads / campaign 表或域实现；事件 props 可预留 `campaign_id` 字段名，但不落地营销/广告 schema。
- **不要**在 Phase 1 强制接入神策 / GA / Mixpanel 等第三方 SDK（文档可写「后续可选」）。
- **不要**改 `docs/content/**`、不要改 `CHANGELOG.md`、不要改前端埋点代码（除非勾选 Phase 2 且 brief 明确要求——**本 brief Phase 1 不要求改 web/api Java**）。
- **不要**让 analytics 服务或文档暗示「分析进程可以直接 `UPDATE wallet`」。

---

## 1. 背景

产品需要用数据支撑 **拉新 / 营销 / 定价**：注册与激活、生成漏斗、按模型成功率、充值与消耗、冻结滞留、ARPU 粗算等。

已拍板原则：

1. 新建 **analytics** 域（或文档中的 bounded context），**不**把埋点与报表逻辑塞进 `job` / `billing` 包变成上帝模块。
2. **钱与任务的真相源**仍是 Postgres 业务表（`users` / `job*` / `wallet*` / `wallet_ledger`）；服务端事实事件应从这些表**派生或旁路记录**，报表可直接 SQL / 副本查询。
3. **前端事件**补「意图与曝光」（打开画布、点提交、看付费墙），带 `anon_id` / `session_id`；登录后关联 `user_id`。
4. **统一事件契约**：`event_name` + `ts` + `user_id?` + `anon_id` + `session_id` + `props`。
5. **analytics 禁止改钱包**（不得 freeze/confirm/release/topup）。
6. **隐私**：埋点与事件表禁止密码、支付密钥、Anytoken/上游 API Key、完整支付凭证。
7. **growth / ads / campaign** 为后续域；本 WP 只预留 props（如 `campaign_id`），不建表。
8. **分阶段**：本 WP 默认 **docs-first**（与 WP13 同类）；采集入库可 Phase 2 / WP15b。

编号说明：ORM 代码迁移占用 **WP14**；数据分析文档基线占用 **WP15**，避免抢号。

---

## 2. 目标（可验证）

### Phase 1（本 PR 默认必须）

1. 新增 **ADR 0007**（Accepted）：analytics 域边界、事件契约、服务端事实 vs 客户端意图、禁止改钱包、隐私红线、与后续 ads/campaign 的关系、Phase 1 文档 / Phase 2 采集。
2. `ARCHITECTURE.md` 增加 **analytics** 域说明：职责 / 不职责；跨域只读业务库或收事件，不写 billing；可选提及后续 growth/ads/campaign。
3. 新增 `docs/engineering/data-model/analytics.md`：**事件字典 v0**（下文章节 5 的表必须落入该文件）+ **必出报表目录**（章节 5.3）；写明 Phase 1 无强制新表。
4. `data-model/README.md` 索引增加 analytics 链接；一句说明「分析事件与报表见 analytics.md；余额真相仍在 billing」。
5. `TECH_STACK.md`：若需，增加「分析 / 埋点」一行——Phase 1 以仓内事件字典 + SQL 报表为准；第三方 SDK **非** stage 冻结依赖（写「可选后续」即可）。
6. `feature-briefs/README.md` 索引增加 WP15；可注明 WP14 预留给 MyBatis-Plus 代码迁移（若 WP14 brief 尚未入仓，写「编号预留」即可，**不要**伪造 WP14 空 brief 文件）。
7. `DEVELOPMENT.md` 增加 WP15 小节；依赖图/文首覆盖到 WP15；**明确 WP14 = ORM 代码迁移（预留）**。
8. `adr/README.md` 索引 +0007。
9. 本 brief 全文入仓：`docs/engineering/feature-briefs/2026-10-25-wp15-analytics-events-reports.md`。
10. （建议）`CONTRIBUTING.md` 增加 1 条：新增用户行为埋点须更新 `data-model/analytics.md` 事件字典；analytics 不得写钱包。

### Phase 2（可选，默认可跳过）

仅当 PR 描述明确写「含 WP15 Phase 2」时才要求：

- Flyway：`analytics_event`（或等价名）最小表 + 索引建议。
- api 域包 `com.flora.api.analytics`：`web` ingest（鉴权策略在 ADR/brief 写清：登录用户或带 anon 的受限接口）+ `application` 只追加事件 + `infra` 仓储；**零**钱包写入。
- 契约字段与字典一致；单测：写入成功、拒绝改钱包路径（无 billing 依赖写操作）。

**未勾选 Phase 2 时，有 Phase 2 代码/迁移反而应打回或标为超出——除非实施者愿意拆成第二 commit 且 checklist 勾选。** 推荐：Phase 1 PR 干净只做文档。

---

## 3. 范围（允许新增 / 修改）

| 路径 | 动作 |
| --- | --- |
| `docs/engineering/feature-briefs/2026-10-25-wp15-analytics-events-reports.md` | **新增**（本文件全文入仓） |
| `docs/engineering/feature-briefs/README.md` | 索引表 +WP15；可选注明 WP14 编号预留给 ORM 代码迁移 |
| `docs/engineering/adr/0007-analytics-events-and-reports.md` | **新增** Accepted ADR |
| `docs/engineering/adr/README.md` | 索引 +0007 |
| `docs/engineering/ARCHITECTURE.md` | 增加 analytics 域；可选「后续域」一句（growth/ads/campaign） |
| `docs/engineering/data-model/analytics.md` | **新增** 事件字典 v0 + 报表目录 + 契约 + 隐私 |
| `docs/engineering/data-model/README.md` | 索引链到 analytics.md |
| `docs/engineering/TECH_STACK.md` | 可选「分析」行：字典 + SQL 优先；第三方 SDK 非冻结 |
| `docs/engineering/DEVELOPMENT.md` | WP15 小节；WP14 预留说明；文首/依赖图更新 |
| `docs/engineering/CONTRIBUTING.md` | 建议 +埋点字典 / 禁 analytics 写钱包 |

**Phase 2 才允许（默认不做）：**

| 路径 | 动作 |
| --- | --- |
| `api/src/main/resources/db/migration/V*__analytics_event.sql`（版本号接仓内最新） | 建表 |
| `api/src/main/java/com/flora/api/analytics/**` | 最小 ingest |
| 对应测试 | 写入 / 校验字段 |

---

## 4. 非范围（本 PR 明确不做）

- **WP14**：MyBatis-Plus 依赖引入、`Jdbc*Repository` → Mapper 全量或逐域迁移（另 brief）。
- 建设 **ads / campaign / growth** 表、优惠券、广告位、投放后台。
- Phase 1 **强制**第三方分析 SDK、数仓、实时 OLAP、Admin 可视化大屏产品化（报表目录只定「要算什么」，不要求本 PR 做 BI 前端）。
- 让 worker / analytics **confirm/release/topup** 钱包。
- 在埋点中收集密码、支付密钥、上游 Key、完整银行卡号等。
- 重写 WP0–WP13 历史 brief 正文；改 `docs/content/**`。
- 另开「修订笔记」代替改 ARCHITECTURE / 新建 analytics.md。

---

## 5. 数据与事件（写入 `data-model/analytics.md` 的正文要求）

### 5.1 统一事件契约

每个事件逻辑上包含：

| 字段 | 类型 / 说明 |
| --- | --- |
| `event_name` | 稳定蛇形/点分小写名，见字典；**禁止**同一行为多名 |
| `ts` | 事件发生时间（UTC 存储，展示转用户区；文档写清） |
| `user_id` | 可空；登录后必填（服务端能确定时） |
| `anon_id` | 客户端匿名 id（未登录漏斗用） |
| `session_id` | 会话 id |
| `props` | JSON 对象；常用键见下 |

**常用 props（按需，未用可省略）：**

`model`、`capability`（如 `image.generate` / `video.generate`）、`job_id`、`hold_id`、`amount`、`currency`（若有）、`ledger_type`、`error_code`、`http_status`（仅客户端）、`page`、`campaign_id`（预留，无表）、`source`（`client` / `server`）、`duration_ms`

### 5.2 事件字典 v0（必须原样落入 analytics.md；可增注释列，不得删行）

**来源约定：**

- **server**：以业务表状态变化或 api 用例成功路径为准（可 SQL 派生，不一定先落 `analytics_event`）。
- **client**：前端曝光/点击；Phase 1 只定名字与 props，不强制改 `web/` 代码。

| event_name | 来源 | 含义 | 关键 props |
| --- | --- | --- | --- |
| `user_registered` | server | 用户注册成功 | — |
| `user_login` | server | 登录成功 | — |
| `canvas_opened` | client | 打开画布/创作页 | `page` |
| `model_selected` | client | 选择模型 | `model`, `capability` |
| `generation_submit_clicked` | client | 点击提交生成（意图） | `model`, `capability` |
| `job_submitted` | server | job 已创建并进入可排队路径（冻款成功等业务成功后） | `job_id`, `model`, `capability`, `hold_id?`, `amount?` |
| `job_succeeded` | server | job 终态成功 | `job_id`, `model`, `duration_ms?` |
| `job_failed` | server | job 终态失败/dead（文档注明是否含 cancelled） | `job_id`, `model`, `error_code` |
| `paywall_viewed` | client | 看到付费墙/余额不足提示 | `page`, `reason?` |
| `recharge_page_viewed` | client | 打开充值页 | `page` |
| `wallet_topup` | server | 入账成功（ledger topup / 加额） | `amount`, `ledger_type` |
| `wallet_freeze` | server | 冻结成功 | `amount`, `hold_id`, `job_id?` |
| `wallet_confirm` | server | 确认扣费 | `amount`, `hold_id`, `job_id?` |
| `wallet_release` | server | 释放冻结 | `amount`, `hold_id`, `job_id?` |

字典文首声明：

- 服务端钱包/任务类指标**优先**直接查 `wallet_ledger` / `job`；事件表是加速与客户端补全，**不是**第二套账本。
- `campaign_id` 等营销 props 预留；**不**在本 WP 建 campaign 表。

### 5.3 必出报表目录（Must-have reports）

写入 analytics.md，每张报表写：目的、主数据源、粒度、核心口径（一句话即可）：

| 报表 ID | 名称 | 主数据源 | 口径要点 |
| --- | --- | --- | --- |
| `R1` | DAU / 激活 | `users` + 登录/首 job | DAU=日活跃（登录或有 server 事件）；激活=注册后首次成功 `job_submitted` 或首次登录（文档选定一种并写死） |
| `R2` | 生成漏斗 | client 事件 + `job` | `canvas_opened` → `generation_submit_clicked` → `job_submitted` → `job_succeeded`；注明 client 未采集前可用 server 段漏斗 |
| `R3` | 按模型成功率 | `job`（+ model 字段） | succeeded / (succeeded+failed) 按 `model`；排除 cancelled 是否计入分母写清 |
| `R4` | 积分/余额消耗与充值 | `wallet_ledger` | 按日 sum：topup vs confirm（消耗）vs release；赠额与真充若 `ledger_type` 可区分则拆开 |
| `R5` | ARPU 粗算 | `users` + ledger | 周期内 topup 总额 / 周期内活跃或付费用户（文档定义付费用户=有 topup） |
| `R6` | 冻结滞留 | `wallet_hold` / `wallet.frozen` | 非终态 hold 数量与金额、账龄；超时未 confirm/release 告警口径 |

Phase 1 **不要求**实现仪表盘 UI；要求文档达到「数据分析师/后端能按口径写出 SQL」的清晰度。可在 analytics.md 附「示例 SQL 草图」可选小节（非验收强制）。

### 5.4 表结构（仅 Phase 2）

若做 Phase 2，建议最小表（列名可微调，语义不可少）：

`analytics_event(id, event_name, ts, user_id NULL, anon_id NULL, session_id NULL, props jsonb, created_at)`  
索引：`(event_name, ts)`、`(user_id, ts)`、`(anon_id, ts)`。  
逻辑 FK → `users.id`，**不**建物理 FK（与 WP13 前向策略一致）。  
**无**余额列；禁止触发器改 `wallet*`。

Phase 1 文档可描述该表为「后续可选物理化」，**不**提交 Flyway。

---

## 6. API

**Phase 1：** 无 HTTP API 变更。

**Phase 2（可选）：** 例如 `POST /api/v1/analytics/events`（路径以仓内现有 API 前缀为准），body 为单条或批量事件；需鉴权或防滥用策略写进 ADR/analytics.md；**只插入事件**。

---

## 7. 状态机

无新业务状态机。job / wallet 状态机不变；报表只读终态与 ledger 类型。

---

## 8. 逐文件修改说明书（照抄执行）

### 8.1 新增 `docs/engineering/adr/0007-analytics-events-and-reports.md`

采用 ADR 固定结构（中文标题）。Decision 要点**不得删**：

```markdown
# ADR 0007 — 数据分析：analytics 域、事件契约与报表口径

- 状态：Accepted
- 日期：2026-10-25

## 背景（Context）

flora 需要用数据支撑拉新、营销与定价：激活与留存、生成漏斗、按模型质量、充值与消耗、冻结滞留等。
若把埋点与报表逻辑塞进 `job` / `billing`，或另建一套「分析用余额」，会破坏领域边界与 ADR 0001 账本唯一性。
客户端点击不能代替账本与任务表成为资金/任务真相源。

## 决策（Decision）

- 设立 **analytics** 域（文档与未来代码包 `com.flora.api.analytics`）：负责事件契约、采集（后续）、报表口径；**不**负责改余额、不实现广告投放与优惠券引擎。
- **真相源分层**：
  - 资金与任务终态：Postgres 业务表（`users` / `job*` / `wallet*` / `wallet_ledger` / `wallet_hold`）为权威。
  - 意图与曝光：客户端事件（`canvas_opened`、`generation_submit_clicked`、`paywall_viewed` 等）。
- **统一事件契约**：`event_name`、`ts`、`user_id?`、`anon_id`、`session_id`、`props`；稳定命名见 `data-model/analytics.md` 事件字典；禁止同一行为多个 `event_name`。
- **analytics 禁止写入钱包**（不得调用 freeze/confirm/release/topup，不得直接 SQL 更新 `wallet*`）。跨域只读或通过只追加事件。
- **隐私红线**：事件与日志不得含密码、支付密钥、Anytoken/上游 API Key、完整支付凭证；用户标识用 `user_id` / 脱敏策略。
- **后续域**：`growth` / `ads` / `campaign` 可独立演进；事件 props 可含 `campaign_id` 等预留键，**本 ADR 不**要求立刻建营销/广告表。
- **落地阶段**：先文档（事件字典 + 必出报表 + 本 ADR + 架构说明）；物理 `analytics_event` 与 ingest API 为后续实现 WP（WP15 Phase 2 或 WP15b）。Phase 1 不强制第三方分析 SDK。
- 与 WP 编号：本决策对应 **WP15**；**WP14** 保留给 MyBatis-Plus 代码迁移，互不合并进同一「顺手 PR」除非另有明确 brief。

## 后果（Consequences）

- 正向：增长/定价讨论有统一指标与埋点语言；资金口径仍可审计。
- 正向：广告/营销将来可挂 props / 新域，而不污染 billing。
- 代价：客户端未埋点前漏斗上游段不完整，须用 server 段指标过渡。
- 代价：需纪律维护事件字典，避免前端随意发明事件名。
- 不做：不授权第二套用户额度；不授权 worker/analytics 写钱包；不在本决策强制上数仓或第三方 SDK。
```

### 8.2 修改 `docs/engineering/adr/README.md`

索引表追加：

| [0007](./0007-analytics-events-and-reports.md) | 数据分析：analytics 域、事件契约与报表口径 | Accepted |

「何时新写」可补：「新增分析域 / 改埋点真相源策略」。

### 8.3 修改 `docs/engineering/ARCHITECTURE.md`

在合适位置（「分层」附近或新小节 **「领域：analytics」**）增加，要点必须有：

1. **analytics**：事件契约与（未来）采集、报表口径；只读业务事实或追加事件；**不**改钱包；**不**做广告/券引擎。
2. 与 `auth` / `billing` / `job` 关系：身份与资金/任务仍在原域；分析消费其数据或事件。
3. 一句 **后续域**：`growth` / `ads` / `campaign` 未建；账本不双写；活动发奖仍走 billing 用例（与既有资金边界一致）。
4. 若文首仍写「当前仓库仍主要是 web/」而与现状不符：做一次诚实 scrub（仅过时句），不借机大重构。

### 8.4 新增 `docs/engineering/data-model/analytics.md`

结构建议：

1. 文首：目的、与 billing/job 真相源关系、链 ADR 0007  
2. 事件契约（§5.1）  
3. 事件字典 v0 全文表（§5.2）  
4. 必出报表 R1–R6（§5.3）  
5. 隐私与禁止字段  
6. 可选物理表（Phase 2 / 后续）说明：**本 WP Phase 1 不建表**  
7. `campaign_id` 等预留 props，无营销表  

### 8.5 修改 `docs/engineering/data-model/README.md`

- 目录/索引增加 [analytics.md](./analytics.md)  
- 一句：分析事件与报表口径见 analytics；**余额与流水权威仍在 billing.md**

### 8.6 修改 `docs/engineering/TECH_STACK.md`

增加一行或一小段（位置自定，避免与「明确不进」冲突）：

| 产品数据分析 | 事件字典 + 业务库 SQL 报表为基线（[ADR 0007](./adr/0007-analytics-events-and-reports.md)、[analytics.md](./data-model/analytics.md)）；第三方分析 SDK / 数仓为后续可选，**非**本阶段冻结必选依赖 |

### 8.7 修改 `docs/engineering/feature-briefs/README.md`

1. 索引表新增：

| [2026-10-25-wp15-analytics-events-reports.md](./2026-10-25-wp15-analytics-events-reports.md) | WP15 | 数据分析：事件字典 v0 + 必出报表 + analytics 域（docs-first；采集可选 Phase 2） | 已确认 / 实现中 |

2. 若仍有「后续 WP13 不要提前写空 brief」等过时句，改为反映 WP13+ 已存在；并加一句：

> **编号**：**WP14** 预留给 MyBatis-Plus **代码**迁移（见 WP13「下一步」）；**WP15** 为数据分析文档基线。勿混用编号。

### 8.8 修改 `docs/engineering/DEVELOPMENT.md`

1. 文首「WP0–WP11」等改为覆盖至 WP15（或「WP0–WP15」）。  
2. 依赖图或文末增加：

```text
WP13 工程文档（MyBatis-Plus 目标，docs）
  ↓
WP14 MyBatis-Plus 代码迁移（预留；另 brief）
WP15 数据分析事件字典与报表口径（docs-first；可与 WP14 并行，不互相阻塞）
```

3. 新增小节：

```markdown
## WP14 — MyBatis-Plus 代码迁移（编号预留）

- **里程碑**：工程实现（跨 M）
- **Brief**：待开（见 WP13「下一步」）；**本编号不分配给分析或其他主题**
- **做什么**：引入 MyBatis-Plus，按域替换 `Jdbc*Repository` 等（细节以届时 brief 为准）
- **不做**：与 WP15 分析文档混在同一无 brief 抢号

## WP15 — 数据分析（事件字典 + 报表口径）

- **里程碑**：增长/定价决策基线（docs-first）
- **Brief**：[2026-10-25-wp15-analytics-events-reports.md](./feature-briefs/2026-10-25-wp15-analytics-events-reports.md)
- **做什么**：ADR 0007、ARCHITECTURE analytics 域、`data-model/analytics.md` 事件字典 v0 与必出报表；索引与 DEVELOPMENT 对齐。
- **不做**：强制第三方 SDK；ads/campaign 表；WP14 ORM 迁移；默认不做 ingest 表（Phase 2 可选）。
- **完成**：文档与 ADR 0007 一致；字典含约定事件名；报表 R1–R6 口径可执行。
```

### 8.9 修改 `docs/engineering/CONTRIBUTING.md`（建议）

- [ ] 新增/变更用户行为事件时，同步更新 [data-model/analytics.md](./data-model/analytics.md) 字典。  
- [ ] analytics 与埋点路径不得写入钱包（遵守 [ADR 0007](./adr/0007-analytics-events-and-reports.md)）。

### 8.10 本 brief 入仓

复制为：`docs/engineering/feature-briefs/2026-10-25-wp15-analytics-events-reports.md`

---

## 9. 验收（严格勾选）

### Phase 1（必须）

- [ ] 分支名 `feat/wp15-analytics-events-reports`（或 PR 说明等价）。
- [ ] **未**将本工作标为 WP14；DEVELOPMENT/README 写清 WP14=ORM 代码迁移预留、WP15=本主题。
- [ ] `docs/engineering/adr/0007-analytics-events-and-reports.md` 存在且 Accepted；Decision 含：analytics 域、契约字段、server vs client、禁写钱包、隐私、后续域 props 预留、分阶段、WP15 vs WP14。
- [ ] `docs/engineering/adr/README.md` 含 0007。
- [ ] `docs/engineering/ARCHITECTURE.md` 含 analytics 职责/不职责。
- [ ] `docs/engineering/data-model/analytics.md` 含事件字典 v0（至少含 §5.2 全部事件名）与报表 R1–R6。
- [ ] `docs/engineering/data-model/README.md` 链到 analytics.md。
- [ ] `docs/engineering/TECH_STACK.md` 有分析基线说明（或等价段落）。
- [ ] `docs/engineering/feature-briefs/README.md` 有 WP15 行 + WP14 预留说明。
- [ ] `docs/engineering/DEVELOPMENT.md` 有 WP15 小节与 WP14 预留。
- [ ] 本 brief 已入仓至 `feature-briefs/2026-10-25-wp15-analytics-events-reports.md`。
- [ ] **没有**强制第三方 SDK；**没有** ads/campaign 表；**没有** WP14 ORM 代码迁移。
- [ ] 默认 **没有** `analytics_event` Flyway（除非 PR 明确 Phase 2 并过下列可选项）。
- [ ] PR 描述引用本 brief，列出勾选与「明确没做 Phase 2 / 没做 WP14」。

### Phase 2（仅声明包含时）

- [ ] `analytics_event`（或文档等价名）迁移与字典字段对齐。  
- [ ] ingest 只追加事件；测试证明无钱包写依赖。  
- [ ] 隐私：样例/日志无密钥与密码。

---

## 10. 非目标（阶段稳定）

- 不 fork NewAPI；不建第二套用户额度（ADR 0001）。  
- Worker / analytics 不 confirm/release 钱包（ADR 0004 / 0007）。  
- Admin 不做第二创作产品。  
- 本阶段不做完整增长营销中台、广告竞价、实时个性化推荐。

---

## 11. 下一步（Out of scope / next）

1. **WP14**（若尚未开）：MyBatis-Plus 代码迁移（见 WP13）。  
2. **WP15b / WP15 Phase 2**：`analytics_event` + ingest + 前端关键 client 事件埋点。  
3. Admin 只读报表页或接 Metabase 等（另 brief）。  
4. `campaign` / `ads` 域与发奖走 billing（另 brief）。

---

## 12. 给 Minimax 的额外约束

- 遵守 `docs/engineering/` 全部 Accepted ADR（含本 WP 的 0007）。  
- 不改无关文件；不重写 `docs/content/`。  
- 密钥用占位符。  
- **改历史文件 + 新建 analytics.md / ADR 0007，不要只写修订笔记。**  
- **WP 编号用 15，不要用 14。**  
- 开 PR，标题建议：`docs(wp15): analytics event dictionary and report catalog`。
