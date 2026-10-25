# 数据分析：事件字典与报表口径

本页是 flora **analytics** 域的工程真相源：统一事件契约、事件字典 v0、必出报表 R1–R6 与隐私红线。架构决策见 [ADR 0007](../adr/0007-analytics-events-and-reports.md)。

## 与 billing / job 的关系

- **资金与任务终态**：`wallet` / `wallet_ledger` / `wallet_hold` 与 `job` 等 Postgres 业务表为权威；服务端钱包/任务类指标**优先**直接查这些表（可 SQL 派生），**不必**先落事件表。
- **事件表**（若后续 Phase 2 物理化）用于加速查询与补全客户端意图，**不是**第二套账本。
- **analytics** 域只读业务事实或只追加事件记录；**禁止** freeze / confirm / release / topup，不得 `UPDATE wallet*`。
- 事件 `props` 可预留 `campaign_id` 等营销键；**本 WP 不建** ads / campaign 表。

**WP15 Phase 1**：本文档即交付物；**不强制**新建 `analytics_event` 表或 ingest API（见文末「可选物理表」）。

## 统一事件契约

每个事件逻辑上包含：

| 字段 | 类型 / 说明 |
| --- | --- |
| `event_name` | 稳定蛇形/点分小写名，见下表字典；**禁止**同一行为多个名字 |
| `ts` | 事件发生时间；**存储与聚合一律 UTC**（`TIMESTAMPTZ`）；展示层再转用户时区 |
| `user_id` | 可空；登录后服务端能确定时必填 |
| `anon_id` | 客户端匿名 id（未登录漏斗用） |
| `session_id` | 会话 id |
| `props` | JSON 对象；常用键见下 |

**常用 props（按需，未用可省略）：**

`model`、`capability`（如 `image.generate` / `video.generate`）、`job_id`、`hold_id`、`amount`、`currency`（若有）、`ledger_type`、`error_code`、`http_status`（仅客户端）、`page`、`campaign_id`（预留，无表）、`source`（`client` / `server`）、`duration_ms`

**来源约定：**

- **server**：以业务表状态变化或 api 用例成功路径为准（可 SQL 派生，不一定先落 `analytics_event`）。
- **client**：前端曝光/点击；Phase 1 只定名字与 props，不强制改 `web/` 代码。

## 事件字典 v0

| event_name | 来源 | 含义 | 关键 props |
| --- | --- | --- | --- |
| `user_registered` | server | 用户注册成功 | — |
| `user_login` | server | 登录成功 | — |
| `canvas_opened` | client | 打开画布/创作页 | `page` |
| `model_selected` | client | 选择模型 | `model`, `capability` |
| `generation_submit_clicked` | client | 点击提交生成（意图） | `model`, `capability` |
| `job_submitted` | server | job 已创建并进入可排队路径（冻款成功等业务成功后） | `job_id`, `model`, `capability`, `hold_id?`, `amount?` |
| `job_succeeded` | server | job 终态成功 | `job_id`, `model`, `duration_ms?` |
| `job_failed` | server | job 终态失败或 `dead`；**不含**用户取消的 `cancelled`（取消单独用 job 状态统计，不记本事件） | `job_id`, `model`, `error_code` |
| `paywall_viewed` | client | 看到付费墙/余额不足提示 | `page`, `reason?` |
| `recharge_page_viewed` | client | 打开充值页 | `page` |
| `wallet_topup` | server | 入账成功（ledger topup / 加额） | `amount`, `ledger_type` |
| `wallet_freeze` | server | 冻结成功 | `amount`, `hold_id`, `job_id?` |
| `wallet_confirm` | server | 确认扣费 | `amount`, `hold_id`, `job_id?` |
| `wallet_release` | server | 释放冻结 | `amount`, `hold_id`, `job_id?` |

新增/变更用户行为事件时，必须先更新本表并走 PR 审查（见 [CONTRIBUTING.md](../CONTRIBUTING.md)）。

## 必出报表目录（R1–R6）

Phase 1 **不要求**实现仪表盘 UI；口径须达到「数据分析师/后端能按说明写出 SQL」。

### R1 — DAU / 激活

| 项 | 说明 |
| --- | --- |
| 目的 | 日活与注册后激活转化 |
| 主数据源 | `users` + 登录记录 / `user_login` 语义 + `job` |
| 粒度 | 日（UTC 日界或业务选定日界，全文一致即可） |
| 口径 | **DAU** = 当日有 `user_login`（或等价：当日有带 `user_id` 的 server 活动，如 `job_submitted`）的去重 `user_id`。**激活** = 注册后**首次** `job_submitted` 成功（`job` 首次进入 `queued` 且关联冻款成功）；不以「仅登录」作为激活定义。 |

### R2 — 生成漏斗

| 项 | 说明 |
| --- | --- |
| 目的 | 从进入到成功生成的转化 |
| 主数据源 | client 事件（未来）+ `job` |
| 粒度 | 日 / 会话（client 就绪后按 `session_id`） |
| 口径 | 理想链：`canvas_opened` → `generation_submit_clicked` → `job_submitted` → `job_succeeded`。**client 未采集前**：用 server 段 `job_submitted` → `job_succeeded`（按 `user_id` 或全站）过渡，文档与看板须标注「缺意图段」。 |

### R3 — 按模型成功率

| 项 | 说明 |
| --- | --- |
| 目的 | 模型质量与稳定性对比 |
| 主数据源 | `job`（`model` / `capability` 字段） |
| 粒度 | 模型 × 日 |
| 口径 | 成功率 = `succeeded` / (`succeeded` + `failed` + `dead`)；**分母不含** `cancelled`（取消视为用户主动退出，不计入失败）。按 `model` 分组。 |

### R4 — 积分/余额消耗与充值

| 项 | 说明 |
| --- | --- |
| 目的 | 现金流与消耗趋势 |
| 主数据源 | `wallet_ledger` |
| 粒度 | 日 |
| 口径 | 按日 `SUM(amount)`：`type` 为 topup（充值/加额）vs confirm（消耗确认）vs release（释放冻结）。若 `ledger_type` 可区分赠额与真充，报表拆开两列。 |

### R5 — ARPU 粗算

| 项 | 说明 |
| --- | --- |
| 目的 | 粗算人均收入（定价参考） |
| 主数据源 | `users` + `wallet_ledger` |
| 粒度 | 周或月 |
| 口径 | 周期内 topup 总额 / 周期内 **活跃用户数**（与 R1 DAU 定义一致的去重 `user_id`）。**付费用户** = 周期内至少一笔 topup；可另算「付费用户 ARPU = topup 总额 / 付费用户数」。 |

### R6 — 冻结滞留

| 项 | 说明 |
| --- | --- |
| 目的 | 发现长期未 confirm/release 的冻结单 |
| 主数据源 | `wallet_hold` + `wallet.frozen` |
| 粒度 | 快照 + 账龄桶 |
| 口径 | 统计 `status` 非终态（仍占用冻结）的 hold **数量与金额**；按 `created_at` 账龄分桶。告警口径（实现时定阈值）：例如账龄 > 24h 且关联 `job` 已终态但仍未 release/confirm 的 hold（数据异常或流程卡住）。 |

## 隐私与禁止字段

事件、`props`、分析日志与样例数据**不得**包含：

- 密码、`password_hash`、会话 token
- 支付密钥、银行卡完整号、支付渠道回调原文
- Anytoken / 上游供应商 API Key
- `provider_task` 级上游堆栈（仅 admin 排障域可见）

用户标识优先 `user_id`；对外导出须遵守后续脱敏策略 brief。

## 可选物理表（Phase 2 / WP15b）

**WP15 Phase 1 不提交 Flyway、不建表。** 若后续做采集入库，建议最小表：

`analytics_event(id, event_name, ts, user_id NULL, anon_id NULL, session_id NULL, props jsonb, created_at)`

建议索引：`(event_name, ts)`、`(user_id, ts)`、`(anon_id, ts)`。逻辑 FK → `users.id`，**不**建物理 FK（与 WP13 前向策略一致）。**无**余额列；禁止触发器改 `wallet*`。

ingest API（示例 `POST /api/v1/analytics/events`）**只插入事件**；鉴权与防滥用策略在届时 brief 写清。

第三方分析 SDK（神策 / GA / Mixpanel 等）为**可选后续**，非 stage-1 冻结依赖。
