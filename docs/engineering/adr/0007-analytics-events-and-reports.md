# ADR 0007 — 数据分析：analytics 域、事件契约与报表口径

- 状态：Accepted
- 日期：2026-10-25

## 背景（Context）

pilipala 需要用数据支撑拉新、营销与定价：激活与留存、生成漏斗、按模型质量、充值与消耗、冻结滞留等。
若把埋点与报表逻辑塞进 `job` / `billing`，或另建一套「分析用余额」，会破坏领域边界与 ADR 0001 账本唯一性。
客户端点击不能代替账本与任务表成为资金/任务真相源。

## 决策（Decision）

- 设立 **analytics** 域（文档与未来代码包 `art.pilipala.api.analytics`）：负责事件契约、采集（后续）、报表口径；**不**负责改余额、不实现广告投放与优惠券引擎。
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
