# Feature brief：WP21 视频多渠道路由与 NewAPI 网关（Phase 1）

- 日期：2026-10-29
- 作者：cavas / pilipala engineering（AI 执行，用户授权）
- 对应 WP / 里程碑：WP21 / M2·M3 视频能力扩展
- 状态：已确认（Phase 0 实测完成；Phase 1 待实现）
- 关联：[ADR 0008](../adr/0008-gateway-layer-video-multi-channel.md)

## 背景

WP12 的视频生成只有一条 Anytoken 适配器，请求体固定，worker 在进程内阻塞轮询 90×2s，租约 60s。面对 meaicc / NewAPI 的 15 个视频变体，这套实现有三个硬伤：

1. 参数表达不了：时长枚举、比例集合、480p/720p/768P/1080p、9/10/30 张参考图、0/3/5/10 个参考视频/音频、`generate_audio`、`mode` 都不在合同里；
2. 长任务撑不住：c4 平均 435s、w3-c1 平均 2028s，现有 180s 轮询和 60s 租约先超时；
3. 没有路由/兜底：只能按 model 名写 if-else，失败就整单重跑，没有同产品多 target 的 fallback。

本 brief 只做 **Phase 1：一个产品端到端 + 执行模型改造**；不铺 13 个变体的 C 端目录，不做按秒计费 UI。

## Phase 0 实测（2026-10-29）

连接：`https://api.meaicc.com`，NewAPI 风格网关；`GET /v1/models` 返回 15 个模型，与 `/api/pricing` 一致。

**成功链路**（c4 / 4s / 1280x720）：

- `POST /v1/video/generations`，body `{"model":"sd-2-c4","prompt":"...","duration":4,"size":"1280x720"}` → HTTP 200，返回 `id` / `task_id`（同一个值）、`status=RUNNING`；
- `GET /v1/video/generations/{task_id}` → 轮询 `RUNNING`，约 **78 秒**后 `status=SUCCEEDED`；
- 成片 URL 在 **`object`** 字段（不是 `result.url` / `url` / `video_url`）：`https://minioapi.meaicc.com/...-output.mp4`；
- 下载 795,903 bytes，`ffprobe` 时长 4.096s；下载不需要 Authorization。

**协议坑（实现必须处理）**：

- `GET /v1/video/generations/{task_id}/content` → **HTTP 404**；不要依赖 content 端点，直接用 `object` 下载；
- `seconds` 字段始终为 0，不能当进度；进度只看 `status`；
- 失败状态不是 `FAILED`，而是 **`FAILED: <message>`** 字符串；不能只做精确匹配；
- 非法参数（如 `duration=99`）不一定同步 400：可能返回 400 + 一个已失败的 task，`status` 里带 `FAILED: ...`；必须把它映射成不可重试的 `VALIDATION_ERROR`（`error_class=param`），且不写重试；
- 不存在的 task → HTTP 400 `{"code":"task_not_exist"}`，不是 404；不要无限轮询。

待 Phase 1/2 验证：参考图/视频/音频、`metadata` 长尾参数、按秒模型（d2）、429/5xx 语义、失败任务是否计费。**Phase 0 不 fork NewAPI**；如果 Phase 1 发现参考素材或参数映射无法用 stock 表达，再薄 fork 视频 adapter。

> 实测使用真实 Key，只存 `/tmp`，不入仓库、不入文档、不入日志。

## 目标

1. 产品 `seedance-2-720p-stable` 端到端跑通：提交 → 查询 → 下载 → TOS → 钱包 confirm。
2. `model_product` / `model_route` / `model_target` 落库；`POST /api/v1/jobs` 按产品解析 target，按产品价冻结。
3. 视频执行改为 **submit + query 拆步**：worker 不再阻塞轮询；`provider_task` 记录 `next_poll_at` / `poll_count`；调度器按到期时间重投。
4. 同一 target 的 429/5xx/查询超时只重试查询，不重新提交；`timeout` 先按 upstream task id 对账，不立刻新建任务。
5. 失败兜底：`c4 → c7 → c8` 同产品链，参数兼容、`cost_amount` 不超过 route 成本上限；不重算用户价格、不静默降质。
6. Admin 能只读看到 job / target / attempt / `provider_task` 的 `error_class`、耗时、上游原文（仅内部）。
7. 现有图片生成与 WP12 视频路径不回归；Anytoken 不改。

## 范围

允许改：

- `api/src/main/resources/db/migration/V18__video_gateway.sql`（新表 + job/job_step/provider_task 增量列）
- `api/.../job/**`：产品解析、target 选择、freeze、query 调度、fallback 策略
- `api/.../admin/**`：只读 product/target/provider_task 查询
- `worker/.../gateway/**`：新增 `NewApiGateway`（submit/query）；保留 Anytoken
- `worker/.../job/**`：submit/query 分流、查询重试分类、TOS 下载
- `docs/engineering/data-model/jobs.md`、`errors.md`、`api/gateway-contract.md`
- `CHANGELOG.md`、`docs/content/docs/progress/pending-test.mdx`
- 单测 + stub 集成测试；不新增 C 端页面

## 非范围

- C 端模型目录 / 商城 UI、13 个原始分组的展示；
- 按秒计费 UI、产品定价调整、充值/会员改动；
- 把 GPT/图片流量迁到 NewAPI；
- 自动熔断/半开探测（Phase 3 再做；本期只做手动 enable/disable）；
- 多租户、团队、退款策略变更；
- 新 API Key 入库；NewAPI Admin 对外暴露。

## 数据表字段

**产品价固定，fallback 不重算用户价格**；上游成本只做毛利分析。

### 新表 `model_product`

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `code` | TEXT | NO | UNIQUE | 与 `POST /jobs` 的 `model` 一致 |
| `capability` | TEXT | NO | | `video.generate` |
| `display_name` | TEXT | NO | | 前端展示名 |
| `description` | TEXT | YES | | |
| `price_credits` | BIGINT | NO | CHECK > 0 | 用户价，冻结额 |
| `param_schema` | JSONB | NO | | canonical 参数约束 |
| `enabled` | BOOLEAN | NO | DEFAULT true | |
| `sort_order` | INT | NO | DEFAULT 0 | |
| `created_at` / `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

### 新表 `model_target`

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `code` | TEXT | NO | UNIQUE | 如 `newapi/sd-2-c4` |
| `gateway` | TEXT | NO | CHECK ∈ `anytoken` \| `newapi` | |
| `upstream_model` | TEXT | NO | | 上游 model 名 |
| `constraints` | JSONB | NO | | 时长/比例/分辨率/参考素材上限 |
| `cost_type` | TEXT | NO | CHECK ∈ `per_call` \| `per_second` | 上游成本口径 |
| `cost_amount` | NUMERIC(12,4) | NO | CHECK >= 0 | 上游成本 |
| `cost_currency` | TEXT | NO | DEFAULT `USD` | |
| `timeout_seconds` | INT | NO | CHECK > 0 | 单 target 总预算 |
| `enabled` | BOOLEAN | NO | DEFAULT true | |
| `created_at` / `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

### 新表 `model_route`

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `product_id` | UUID | NO | FK → `model_product.id` | |
| `target_id` | UUID | NO | FK → `model_target.id` | |
| `priority` | INT | NO | UNIQUE (`product_id`, `priority`) | 小的先走 |
| `max_cost_amount` | NUMERIC(12,4) | YES | | 该跳允许的最高上游成本，与 target.cost_currency 同币种；`per_second` 时按每秒成本；空=不限制 |
| `enabled` | BOOLEAN | NO | DEFAULT true | |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

> 预置：`seedance-2-720p-stable` → c4(10) → c7(20) → c8(30)；c1/c2/c3 不进稳定产品。

### `job` 增量

| 字段 | 类型 | 空 | 说明 |
| --- | --- | --- | --- |
| `product_id` | UUID | YES | 老 job 为空；新 job 必填 |
| `target_id` | UUID | YES | 当前/最后 target |
| `quoted_price` | BIGINT | YES | 冻结时的产品价快照 |
| `pricing_version` | TEXT | YES | 定价版本 |

### `job_step` 增量

| 字段 | 类型 | 空 | 说明 |
| --- | --- | --- | --- |
| `target_id` | UUID | YES | 本 attempt 的 target；fallback 新开 attempt |
| `error_class` | TEXT | YES | `transient/content/param/auth/task_failed/timeout/download` |

### `provider_task` 增量（现 7 列）

| 字段 | 类型 | 空 | 说明 |
| --- | --- | --- | --- |
| `target_id` | UUID | YES | |
| `upstream_task_id` | TEXT | YES | NewAPI task id |
| `status` | TEXT | NO | DEFAULT `legacy`；CHECK ∈ `submitted` \| `polling` \| `succeeded` \| `failed` \| `cancelled` \| `legacy` | 存量 / Anytoken 旧路径 `legacy`；NewAPI 新行显式写 `submitted` |
| `next_poll_at` | TIMESTAMPTZ | YES | 调度器按此重投 |
| `poll_count` | INT | NO | DEFAULT 0 |
| `last_poll_at` | TIMESTAMPTZ | YES | |
| `error_class` | TEXT | YES | 同上 |
| `latency_ms` | INT | YES | 提交/查询耗时 |
| `cost_amount` | NUMERIC(12,4) | YES | 上游成本快照 |
| `cost_currency` | TEXT | YES | |

索引：`provider_task (status, next_poll_at)` 部分索引 `WHERE status IN ('submitted','polling')`。

> 迁移时存量 `provider_task` 行取默认 `status='legacy'`，调度器只扫 `submitted` / `polling`；Anytoken 旧路径的新行也不写 `submitted`，避免被视频调度器误扫。只有 NewAPI 新行显式进入轮询状态。

## API

### 现有 `POST /api/v1/jobs`

- 入参 `model` 支持产品 code（如 `seedance-2-720p-stable`）；老 model 名继续走现有 Anytoken 路径（`product_id` / `target_id` 可空），不要求迁移。
- 校验 `param_schema`；按 `model_route.priority` 选首个 enabled 且参数兼容的 target。
- 冻结 `model_product.price_credits`，写 `job.product_id / target_id / quoted_price`；`job_step.attempt=1`。
- 不新增 C 端接口；`GET /jobs/{id}` 字段不变。

### Admin（只读）

- `GET /api/v1/admin/model-products`
- `GET /api/v1/admin/model-targets`
- `GET /api/v1/admin/provider-tasks?job_id=`
- `PATCH /api/v1/admin/model-targets/{id}` body `{ "enabled": false }`（仅 enable/disable）

### 网关客户端

- `POST {PILIPALA_NEWAPI_BASE_URL}/v1/video/generations`
- `GET  {PILIPALA_NEWAPI_BASE_URL}/v1/video/generations/{task_id}`
- 认证 `Authorization: Bearer ${PILIPALA_NEWAPI_API_KEY}`；Key 只从 env 注入。
- 新 env：`PILIPALA_NEWAPI_BASE_URL`、`PILIPALA_NEWAPI_API_KEY`、`PILIPALA_NEWAPI_TIMEOUT_MS`、`PILIPALA_PROVIDER_POLL_SCAN_MS`、`PILIPALA_PROVIDER_POLL_BATCH_SIZE`。

## 状态机

`job` 状态不变：`created → queued → running → succeeded | failed | cancelled | dead`。轮询期间 job 保持 `running`，但 worker 不持有长租约。

> 现有 lease/recover 扫描必须以 `provider_task` 为准：有 `submitted` / `polling` 任务的 job 不能被当成“running 超时”重投，否则会重复提交；没有活跃 `provider_task` 的 running job 才允许回收。

`provider_task` 新增子状态：

```
submitted → polling → succeeded
                    ↘ failed
                    ↘ cancelled
```

- submit 成功：写 `upstream_task_id`、`status=polling`、`next_poll_at`；worker 释放租约；
- 调度器扫到期任务，投 `job_id`；worker 查询；
- 查询瞬时失败：`status` 仍 `polling`，`next_poll_at` 退避，不新建任务；
- 查询终态失败：`status=failed`；api 决定同 target 重试或 fallback；
- 查询成功：下载 → TOS → `succeeded`；job confirm；
- 超预算：`GATEWAY_TIMEOUT`；先按 `upstream_task_id` 对账一次，仍未知再失败/兜底。

## 重试与兜底

| 情况 | 动作 |
| --- | --- |
| 提交 429/5xx/网络超时，且无 task id | 同 target 重试提交，最多 2 次（默认，待确认）；带 Idempotency-Key |
| 已有 task id | 绝不重新提交；只查询 |
| 查询 429/5xx/超时 | 同 task 原地退避重查，不新建 attempt |
| 内容审核/人脸/参数拒绝 | 不重试；只切到参数兼容的 target，且用户未要求降质 |
| 401/402/403 | 不重试；target 置 disabled，告警 |
| 上游终态 failed | 切下一个 route target；新 `job_step` attempt |
| 全部失败 | `dead`/`failed`，release 冻结，`NO_AVAILABLE_CHANNEL` 或 `PROVIDER_UNAVAILABLE` |
| 下载/TOS 失败 | 只重试下载，不重新生成 |

- fallback 不重算用户价格；`job.quoted_price` 不变，hold 不变。
- 候选 target 必须参数兼容（同 canonical schema 子集）且 `cost_amount` 不超过 `model_route.max_cost_amount`。
- 不静默降质：分辨率/时长/音频/比例变化一律不算 fallback。
- 默认预算：单 target `timeout_seconds` 由表配；总预算 = 所有 route 之和；具体值待上线前确认。

## 验收

- [ ] Flyway V18 在空库和 V17 库上都能迁移。
- [ ] `POST /jobs` 用 `seedance-2-720p-stable` 创建 job，冻结额 = 产品价；`job_step.target_id` 指向 c4。
- [ ] worker 调 NewAPI 提交成功，写 `upstream_task_id`、`status=polling`、`next_poll_at`，释放租约。
- [ ] 调度器到期重投；查询成功后下载/TOS，job `succeeded`，hold confirm。
- [ ] 模拟查询 500：不新建 upstream task，`next_poll_at` 退避，最终成功。
- [ ] 模拟 c4 终态失败：切 c7，新增 `job_step` attempt；不二次冻结；不重复扣费。
- [ ] c4/c7/c8 全失败：job 终态失败，release，错误码稳定。
- [ ] 高于 route 成本上限的 target 被跳过；参数不兼容的 target 被跳过。
- [ ] Admin 只读能看到 target/attempt/`error_class`/耗时/raw；C 端响应不含 raw。
- [ ] 现有图片生成、WP12 视频路径回归通过。
- [ ] 单测覆盖：route 选择、schema 校验、重试分类、fallback、成本上限。
- [ ] 真机 smoke：一个 c4 任务成功，TOS URL 可读，无重复 upstream task。
- [ ] 仓库/日志/文档无真实 Key。

## 非目标

- 不做 C 端模型商城/原始分组选择；
- 不做按秒计费 UI 与产品定价调整；
- 不做 GPT/图片迁移；
- 不做自动熔断（Phase 3）；
- 不把 NewAPI 当第二套用户/额度系统；
- 不在 worker 里做画布/产品路由/计价。

## 给实现者的额外约束

- 遵守 ADR 0004 / 0005 / 0006 / 0008；worker 不拉画布、不做路由/定价。
- 先改 `docs/engineering/data-model/jobs.md` 与 `api/gateway-contract.md`，再写迁移。
- 密钥只用 env 占位；不写真实 Key；NewAPI Admin 只内网。
- 新的超时/重试/预算默认值必须在本 brief 确认后写入，禁止静默加经验值。
- Phase 1 只接 `seedance-2-720p-stable` 一个产品；c1/c2/c3 不进入稳定链。
- 如果 stock NewAPI 无法表达参考素材/参数，再开薄 fork 的单独 brief，不在本 WP 里顺手改 NewAPI。
