# ADR 0009 — Fork NewAPI 作为独立视频网关：渠道级策略下沉

- 状态：Accepted
- 日期：2026-10-29
- 修订：ADR 0008 第 2/3/6 条；ADR 0001 的“不 fork NewAPI”约束由本 ADR 再次修订

## 背景（Context）

WP21 Phase 1 用外置 NewAPI + pilipala worker 跑通了视频 happy path，但把 submit/query 拆步、轮询、target fallback 留在了 pilipala。Phase 1 之后的错误矩阵暴露出归属问题：

- 429/5xx 退避、401/402/403 熔断、轮询 deadline、task 对账、跨渠道 fallback、内容/参数错误分类，本质都是**渠道级策略**；
- 这些逻辑放在 pilipala worker，会让 worker 逐步变成 mini-gateway，和 ADR 0008 “worker 保持瘦”冲突；
- NewAPI 是独立端口、独立库、独立进程的完整服务，且 v1.0.0-rc.47 已有 task plugin、channel 优先级/权重、任务轮询、超时/退款、任务日志和 Admin 渠道管理，适合承载渠道级策略；
- 用户已确认商业授权，并选择 fork NewAPI，而不是只做外置 HTTP 下游。

## 决策（Decision）

1. **Fork 基线**：fork `QuantumNous/new-api`，pin `v1.0.0-rc.47`（commit `e835b1b`），分支 `pilipala/gateway`；不跟 `main`。升级必须按 tag 评估 task plugin API 变更。
2. **独立部署**：NewAPI 独立端口（本地 `13000`）、独立数据库（复用 Postgres 实例，库名 `pilipala_newapi`）、独立 Redis DB index（复用实例，`/1`）；不与 pilipala 共表。
3. **职责边界**：
   - NewAPI：channel/group、渠道 Key、参数组装、上游协议、submit/poll/cancel、429/5xx 退避、401/402/403 熔断、轮询 deadline、task 对账、允许集合内的 target fallback、错误分类、渠道成本/健康度。
   - pilipala：product、售价、wallet hold/ledger、job 生命周期、TOS、内容 fallback 的用户同意、最终 release、C 端 i18n 文案。
4. **路由**：pilipala 下发 `product → 允许的 group/target 集合 + 预算 + timeout + consent`；NewAPI 只在允许集合内选择和 fallback；跨产品/跨价格不归 NewAPI。
5. **单一账本**：NewAPI 不建 C 端用户/钱包/额度；每个环境一个 service token；NewAPI 的 quota/log 只做上游成本观测，不映射成用户余额。
6. **内部 API**：submit/query/cancel + 可选回调；字段包含 product/group、allowed targets、params、constraints、budget、timeout、`allow_content_fallback`；返回 task 状态、final target/channel、attempts、upstream cost、`error_class`/`error_code`/`retryable`/`suggested_action`、output URL；`raw_message` 只进 Admin/日志。
7. **错误/i18n**：NewAPI 只返回机器字段和动作建议；pilipala 查 `i18n_message` 输出本地化文案；未知 code 回落到 pilipala `PROVIDER_UNAVAILABLE` 并告警；不裸透传上游原文。
8. **复用优先**：优先用 NewAPI task plugin 承载 meaicc 视频变体（c4/c7/c8/sd-2.5/w3）；只有 per-task deadline、跨 target fallback、预算/consent、幂等这些 host 未暴露的能力才改核心，保持 patch 集最小。
9. **TOS**：NewAPI 保证 output/artifact URL 可取；pilipala 负责下载 → TOS，下载失败只重试下载，不重新生成。
10. **Phase 1 代码迁移**：worker 的 NewAPI client、polling、`ProviderPollScheduler`、`VideoFallbackScheduler` 下线；`model_target`/`model_route` 收缩为产品→group/预算，不再镜像渠道。

## Phase 0 基线（已验证）

- fork：`ericlliaocn-hue/new-api`，本地 `/Users/eric/workspace/pilipala-newapi`，分支 `pilipala/gateway`，tag `v1.0.0-rc.47`；
- 独立实例：`13000`，Postgres `pilipala_newapi`（38 张表），Redis `56379/1`，Admin setup 成功；
- task plugin runtime：generation 12，`plugin_error_count=0`；
- 源码结构：`plugins/tasks/*/plugin.js`、`relay/channel/task/jsplugin/*`、`service/task_polling.go`；
- host 已拥有 persistence/retries/polling/billing/settlement（plugin 只做 request/parse/usage 变换）。

## 后果（Consequences）

- 正向：渠道级策略归独立服务，pilipala 回到产品/账本边界；复用 NewAPI 的渠道、分组、任务和 Admin。
- 正向：错误分类和 i18n 解耦；上游原文不进 C 端。
- 代价：维护 Go fork、跟上游同步、task plugin API 仍是 unreleased；需要核心 patch 承载 deadline/fallback/budget/幂等。
- 代价：NewAPI 自带 quota/billing，需要显式隔离，避免第二套账本。
- 风险：`v1.0.0-rc.47` 是 rc；AGPL-3.0 + 商业授权的条款需在发布前确认。

## 非目标

- 不在 NewAPI 建 C 端用户/钱包/额度/产品价；
- 不把 channel/group 表镜像进 pilipala；
- 不返回上游 raw message/渠道名/URL 给 C 端；
- 不 fork qfzy-api，不迁移 GPT/图片存量；
- 不在 Phase 2 做 C 端视频产品入口的完整 UI（另开 brief）。
