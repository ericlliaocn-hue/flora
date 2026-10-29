# Feature brief：WP21 Phase 2 — Fork NewAPI 独立视频网关

- 日期：2026-10-29
- 作者：DSH / pilipala engineering
- 对应 WP / 里程碑：WP21 Phase 2
- 状态：实现中（Phase 0 受控实例/源码构建通过；Phase 1 契约/迁移待落地）

## 背景

WP21 Phase 1 跑通了 `model_product → model_route → model_target → worker → NewAPI` 的视频 happy path，但把 submit/query 拆步、轮询、跨 target fallback 留在了 pilipala。错误矩阵（429/5xx、401/402/403、参数/schema、内容拒绝、上游 task 失败、轮询超时、下载/TOS、全部失败）暴露出归属错误：

- 渠道级重试、熔断、对账、fallback 应该在独立 NewAPI；
- pilipala 只应保留产品、售价、钱包、job、TOS、用户同意和最终 release；
- C 端友好文案和 i18n 必须由 pilipala 统一输出，NewAPI 只出机器字段。

ADR 0009 已接受“fork NewAPI 作为独立视频网关”。本 brief 定义 Phase 2 的具体实现。

## Phase 0 gap analysis（基于 rc.47 源码与运行实例）

**已确认可复用：**

- task plugin host 拥有 persistence、retries、polling、billing、settlement；plugin 只做 request 构造、响应解析、usage facts、错误上报。
- host 支持 `openai_video` 公共协议（`POST /v1/videos`、`GET /v1/videos/:id`、`/content`）、`per_task`/`batch` fetch、plugin state、artifact 投影与 content 代理。
- channel/group、channel priority/weight、任务超时/退款/日志、Admin 渠道管理已存在。
- 轮询失败有 `TASK_POLL_MAX_FAILURES`（默认 20）；全局任务超时 `TASK_TIMEOUT_MINUTES`（默认 4320）；同步协议超时 `TASK_PLUGIN_PROTOCOL_TIMEOUT_SECONDS`（默认 600）。

**缺口（按优先级）：**

- **P0 per-task deadline**：host 只有全局 72h，没有 per-target/per-product deadline；c4 的 900s 目标超时必须由 fork 核心补，否则 hold 可能长时间冻结。
- **P0 跨 target fallback**：task 创建时绑定 channel，终态失败后 host 不会自动换 channel 重新提交；需要在 fork 核心增加“允许集合内换 target”策略，或由 host 明确回调 pilipala 后重新提交（与“渠道级策略在 NewAPI”冲突，优先核心）。
- **P0 submit 幂等/对账**：目前未看到 plugin submit 的 client request id/幂等键；submit 超时可能重复建 task。需要 fork 增加 request id/幂等/对账，或明确 fail-closed。
- **P0 计费隔离**：host 有 quota/billing/settlement；必须只用 service token + 仅成本观测，不能让它成为第二套用户账本。
- **P0 鉴权/额度熔断**：host 只记日志/失败计数，没有 target/channel 级 fuse；需要 fork 增加熔断状态与管理。
- **P1 退避**：host 会累计 poll failures，但轮询循环没有指数退避/jitter；需要 fork 增加 backoff。
- **P1 error taxonomy/action**：plugin 的 `error` 是 user-readable message；没有统一 `error_class`/`error_code`/`retryable`/`suggested_action` 契约；需要 fork 定标准字段，pilipala 只做 i18n。
- **P1 content/param/budget**：host 没有产品级 content policy、用户 consent、target constraints、budget；需要 plugin 校验 + fork 核心策略，或由 pilipala 下发元数据。
- **P2 观测/Admin**：attempts、fuse、deadline、upstream cost 的 Admin 视图和告警需要补；多节点不在本期。

## 实现进度（2026-10-29）

- **task plugin 原型**：NewAPI fork `25936dc`，`plugins/tasks/pilipala-video/plugin.js` + `fixture.json`；`openai_video` / `per_task`，c4/c7/c8，submit → meaicc `/v1/video/generations`，query 按 task id，content 走 credentialless 输出 URL；fixture 7/7。
- **type-61 fake channel E2E**：本地 channel `pilipala-fake-video` + service token；`POST /v1/videos` → query → content 返回 1.1MB MP4；fake upstream 在 18091。
- **per-task deadline**：fork `fc0d894`；`parseSubmitResponse` 从 `X-Pilipala-Deadline-At` / `X-Pilipala-Timeout-Seconds` 写 `plugin_state.deadlineAt`；过期后 task `FAILURE`，已验 `fail_reason=task deadline exceeded`；fixture 9/9。
- **auth fuse**：fork `a0e35b6`；轮询 401/402/403 立即失败 task 并按 `channel.AutoBan` 自动禁用渠道，不再累计 poll failure 重试；已验证 channel2 返回 401 后 `status=3`、task `FAILURE(upstream auth failure)`，channel1 恢复后同模型任务成功。
- **submit 幂等**：fork `3c29c75`；`X-Pilipala-Request-Id` → `tasks.client_request_id`（user 维度唯一）；重复提交返回同一 public task id，不再提交上游；已验证两次提交同一 id、DB 1 行、fake 上游仅 1 个 task。
- **未做**：跨 target fallback、NewAPI billing 隔离；真实 meaicc channel 未接；当前 service token 仍走 NewAPI 内部 quota（`billing_source=wallet, token_id=1`），pilipala 钱包仍是唯一用户账本。



## 目标

1. NewAPI 独立部署（端口 `13000`，Postgres 库 `pilipala_newapi`，Redis `56379/1`），与 pilipala 共中间件实例但独立库/index。
2. 用 NewAPI task plugin 承载 meaicc 视频变体（c4/c7/c8/sd-2.5/w3），渠道/分组在 NewAPI Admin 管理。
3. 渠道级策略全部在 NewAPI：429/5xx 退避、401/402/403 熔断、轮询 deadline、task 对账、允许集合内 fallback、错误分类、upstream cost/attempts。
4. pilipala 只下发 product/group/允许集合/预算/timeout/consent，接收标准化状态；删除 worker 的视频 polling/fallback。
5. 错误分层：NewAPI 出 `error_class`/`error_code`/`retryable`/`suggested_action`；pilipala 出 `i18n_message` 友好文案；raw 只进 Admin。
6. 单一账本：NewAPI 不拥有 C 端用户/余额/产品价；service token 只做上游调用。
7. TOS 归 pilipala：NewAPI 保证 output/artifact URL；pilipala 下载→TOS，失败只重试下载。
8. 全链路 smoke：c4 成功、c4 失败→c7、全部失败→release、内容拒绝→consent、401→熔断、参数错误→兼容切换。

## 范围

- `/Users/eric/workspace/pilipala-newapi`（fork，`pilipala/gateway` 分支）：
  - `plugins/tasks/pilipala-video/plugin.js`（或等价 task plugin）；
  - `relay/channel/task/jsplugin` 只在 host 未暴露能力时改核心；
  - Admin 渠道/分组/任务日志。
- pilipala：
  - `api`：`model_product_group`、内部网关客户端、错误/i18n 映射、job 状态、Admin 只读/迁移；
  - `worker`：删除 NewAPI polling/fallback；保留通用 job/TOS 能力；
  - `docs/engineering`：ADR 0009、本 brief、runbook。
- 数据库：`model_product_group`（新）；`provider_task` 停止新 polling；`model_target`/`model_route` 降级为 legacy/回滚。
- NewAPI 配置：`SQL_DSN`、`REDIS_CONN_STRING`、`PORT=13000`、`SESSION_SECRET`、`TASK_TIMEOUT_MINUTES`、`TASK_POLL_MAX_FAILURES`、`TASK_PLUGIN_ENABLED`。

## 非范围

- 不做 C 端视频产品入口的完整 UI（另开 brief）。
- 不迁移 GPT/图片存量到 NewAPI；Anytoken 保持现状。
- 不把 NewAPI 的 quota/log 映射成用户余额；不做第二套账本。
- 不做 qfzy-api fork；不把 channel/group 表镜像进 pilipala。
- 不做 NewAPI 多节点/高可用；Phase 2 单实例。
- 不把 `raw_message`、渠道名、上游 URL 返回 C 端。

## 数据表字段

### 新表：`model_product_group`

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | UUID | PK | |
| `product_id` | UUID | FK `model_product`, ON DELETE CASCADE | |
| `group_code` | TEXT | NOT NULL | NewAPI group code（稳定标识，不是显示名） |
| `priority` | INT | NOT NULL, ≥0 | 产品内 group 顺序 |
| `max_cost_amount` | NUMERIC(12,4) | NULL | 该 group 的上游预算上限；NULL 表示不限制 |
| `enabled` | BOOLEAN | NOT NULL DEFAULT true | |
| `created_at` / `updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() | |

唯一约束：`(product_id, group_code)`、`(product_id, priority)`。

### 增量字段：`job`

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `gateway_group` | TEXT | NULL | 提交时选中的 NewAPI group |
| `gateway_task_id` | TEXT | NULL | NewAPI 公共 task id；不存上游私有 id |
| `gateway_target` | TEXT | NULL | NewAPI 最终 target/channel 标识 |
| `error_class` | TEXT | NULL | 内部错误分类；C 端仍用 `error_code` |

### 降级/停止

- `provider_task`：保留历史；不再由 pilipala 写入 polling 行；NewAPI task 日志为准。
- `model_target` / `model_route`：保留只读用于 Phase 1 回滚；Phase 3 再删。
- `wallet_hold` / `wallet_ledger` / `i18n_message`：结构不变；补错误码种子。

## API

### NewAPI 内部 API（fork 提供）

- `POST /internal/v1/video/tasks`
  - Header：`Authorization: Bearer <service_token>`，`X-Contract-Version: 1`
  - Body：`request_id`、`product`、`group`、`allowed_groups`/`allowed_targets`、`params`、`constraints`、`budget`、`timeout_seconds`、`allow_content_fallback`
  - 返回：`task_id`、`status`、`final_target`/`final_channel`、`attempts`、`upstream_cost`、`error_class`、`error_code`、`retryable`、`suggested_action`、`output_url`
- `GET /internal/v1/video/tasks/{task_id}`
- `POST /internal/v1/video/tasks/{task_id}/cancel`
- 可选回调：`POST pilipala /internal/gateway/video/callback`，HMAC + 重试；pilipala 幂等。
- 兼容层：如保留 `/v1/videos` OpenAI 协议，只作为调试/兼容，不作为 pilipala 主契约。

### pilipala C 端

- 路径/字段保持 `/api/v1/jobs/submit`、`GET /jobs/{id}`、`GET /jobs/{id}/result`。
- C 端错误：`error_code`（稳定）+ `message`（本地化）；`suggested_action` 仅作为前端交互提示。
- 未知 NewAPI code → `PROVIDER_UNAVAILABLE` + 告警；不返回 raw。

### 错误分类 / action（NewAPI → pilipala）

| `error_class` | 例子 | NewAPI 行为 | pilipala C 端 |
| --- | --- | --- | --- |
| `TRANSIENT` | 429/5xx/网络 | 指数退避；query 原地重试 | 重试/稍后 |
| `AUTH_QUOTA` | 401/402/403 | 熔断 target/channel + 告警 | 服务暂不可用 |
| `PARAM_SCHEMA` | 时长/比例/字段 | 切兼容 target；无兼容→`ADAPTER_BUG` | 参数不支持/改提示词 |
| `CONTENT_REFUSED` | 审核/人脸 | 仅 `allow_content_fallback` 时切 content-policy 允许的 target | 内容提示 + consent |
| `UPSTREAM_FAILED` | task final failed | 按 target `retry_on_failure`；否则跨 target | 生成失败/重试 |
| `POLL_TIMEOUT` | 超 deadline | 按 task id 最终查询；确认丢失才新建/兜底 | 超时/重试 |
| `DOWNLOAD_FAILED` | 下载/TOS | 不重生成；只重试下载 | 结果暂不可用 |
| `NO_AVAILABLE_CHANNEL` | 全部失败 | release 语义交 pilipala；返回最终失败 | `PROVIDER_UNAVAILABLE` |

`suggested_action` 枚举：`RETRY_LATER` / `EDIT_PROMPT` / `SWITCH_CHANNEL` / `CONTACT_SUPPORT`；C 端文案由 pilipala 映射。

## 状态机

**pilipala job：**

```
created → queued → running → succeeded
                         ↘ dead（最终失败/取消/超时）
                         ↘ cancelled
```

- 不再出现 Phase 1 的 `failed` 中间态等待 pilipala fallback；NewAPI 内部完成 target fallback 后才返回最终失败。
- `wallet_hold`：succeeded → confirm；dead/cancelled → release；running 期间保持 frozen。

**NewAPI task：**

```
SUBMITTED → IN_PROGRESS → SUCCESS
                        ↘ FAILURE
```

- 内部 target fallback 不产生新的 pilipala job；最终 `final_target`/attempts 返回给 pilipala。
- poll deadline 默认按 target timeout；`TASK_TIMEOUT_MINUTES` 只作为全局兜底，不作为用户等待时间。

## 验收

- [ ] NewAPI 独立实例可从 fork 源码构建并启动；`13000` 健康；Postgres `pilipala_newapi` 迁移成功；Redis `/1` 连接成功。
- [ ] NewAPI Admin 可新增/编辑 channel/group；pilipala 不保存 channel Key。
- [ ] task plugin 能提交 c4/c7/c8 的视频任务；参数差异由 plugin/constraints 处理，不按 model 名写散落 if-else。
- [ ] 429/5xx → 退避后同 target/兼容 target；query 不重建 task。
- [ ] 401/402/403 → 熔断 + 告警；不烧 fallback。
- [ ] 400 参数错误 → 兼容 target 或 `PARAM_SCHEMA`，不盲目重试。
- [ ] `FAILED:` 内容拒绝 → 无 consent 不自动切卡脸 target；有 consent 才切允许 target。
- [ ] poll deadline 到期 → 按 task id 最终查询；确认丢失才新建/兜底；不无限轮询。
- [ ] 下载/TOS 失败 → 只重试下载；不重新生成。
- [ ] 全部失败 → 恰一次 release；C 端 `PROVIDER_UNAVAILABLE`；raw 只在 Admin。
- [ ] pilipala worker 不再包含 NewAPI polling/fallback；旧 in-flight job 可 drain/回滚。
- [ ] `mvn -pl api,worker -am test` 通过；NewAPI plugin lint/fixture 通过；全链路 smoke 通过。
- [ ] 文档：ADR 0009、本 brief、runbook、AGENTS.md 的“不 fork NewAPI”约束更新。

## 非目标

- 不在本阶段做 C 端完整视频产品 UI。
- 不迁移 Anytoken GPT/图片。
- 不做 NewAPI 多节点/高可用、不做第二套账本。
- 不把 NewAPI 的 raw 错误、渠道名、上游 URL 暴露给 C 端。

## 给实现的额外约束

- 遵守 ADR 0009、ADR 0008（单账本、无用户/额度进网关）、ADR 0005（raw 只在 Admin）。
- NewAPI pin `v1.0.0-rc.47`；升级先跑 task plugin fixture 与契约测试。
- 新的超时/重试/限额必须出现在本 brief；禁止静默加经验值。
- AGPL-3.0 + 商业授权：fork/部署/分发前确认授权范围；保留版权头。
- 先写契约测试，再改实现；每个错误类至少一个 fixture/集成用例。
