# ADR 0008 — 视频多渠道路由：Anytoken 存量 + NewAPI 网关 + 单一账本

- 状态：Accepted
- 日期：2026-10-29
- 修订：ADR 0001（“只用 Anytoken，不 fork NewAPI”）由本 ADR 修订；本 ADR 第 2/3/6 条由 ADR 0009 修订（fork NewAPI 独立视频网关）

## 背景（Context）

WP12 的视频生成只有一条 Anytoken 适配器：请求体固定为 `model / prompt / aspect_ratio / duration / resolution / reference_images / reference_videos`，worker 在进程内 90×2s 阻塞轮询，Redis 租约 60s。

现网供给（meaicc / NewAPI）有 15 个视频变体，差异不是“换一个 model 名”：

- 时长 4-10s / 4-15s / 5-30s / 自由时长；
- 比例只能竖屏 / 固定 3 种 / 全比例；
- 480p / 720p / 768P / 1080p；
- 参考图 9 / 10 / 30，参考视频 0 / 3 / 5 / 10，参考音频 0 / 3 / 5 / 10；
- 人脸策略“卡脸 / 不卡脸 / 原生真人”；
- 按次计费 / 按秒计费；
- 24h 成功率 47%–96%，平均耗时 435s–2028s。

现有 180s 轮询 + 60s 租约在 c4（平均 435s）、w3-c1（平均 2028s）这类渠道上会先超时；再把它们塞进同一个固定 payload，只能靠大量 if-else。

ADR 0001 的出发点是正确的：不要让业务库同时拥有用户、额度、渠道、上游协议，也不要 fork NewAPI 做第二套业务后端。但它把三件事绑在了一起：

1. 账本唯一（必须保留）；
2. 不 fork NewAPI 做业务后端（必须保留）；
3. 所有模型流量只经过 Anytoken（这是拓扑选择，不是账本不变量）。

Anytoken 已经承载 GPT / 图片存量，不宜为视频多渠道路由大改；NewAPI 已有商业授权，渠道、分组、模型映射、渠道级重试/负载均衡更成熟。真正要避免的是：第二套用户/额度账本、worker 变成 mini-gateway、产品定价/路由散落在网关和 worker 两处。

## 决策（Decision）

1. **单一账本**：用户、钱包、job、产品、价格、freeze/confirm/release 只存在 pilipala。NewAPI 不建 C 端用户/钱包/额度；每个环境只用一个 service token；它的 quota/log 只做成本观测，不映射成用户余额。
2. **网关分层**：
   - **Anytoken**：继续承载现有 GPT / 图片 / 已上线 Seedance 路径，本期不改。
   - **NewAPI**：独立部署，承载视频 / 多渠道。负责渠道 Key、上游协议、模型映射、渠道级重试/负载均衡、任务日志、渠道健康。
   - **pilipala**：负责产品 SKU、价格、canonical 参数、target schema、产品级 fallback、成本上限、用户错误、job/wallet 状态。
3. **路由归属**：产品级路由/fallback 在 pilipala 的 api/domain；渠道级重试/负载均衡在 NewAPI；worker 只执行一个 target，不拉画布、不选产品、不重算价格。
4. **Canonical video request**：`prompt / duration_s / aspect_ratio / resolution / reference_images / reference_videos / reference_audios / generate_audio / mode / seed / watermark`。业务与前端只传这套；每个 target 用声明式 mapping 转上游，禁止按 model 名写分支。
5. **产品与 target**：`model_product` → `model_route` → `model_target`。target 带 constraints、cost、timeout、enabled；fallback 只走同产品、参数兼容、`cost_amount` 不超过 `model_route.max_cost_amount` 的 target。产品价固定，成本差是平台毛利；不做静默降质/涨价。
6. **异步执行**：submit 与 query 分离；`provider_task` 记录 upstream task id、`next_poll_at`、`poll_count`、`status`；调度器按 `next_poll_at` 重投；worker 不长时间持有租约。timeout ≠ failure，先按 task id 对账再决定重试/兜底。
7. **计费**：按产品价冻结；fallback 不重算用户价格；上游成本只记 `provider_task.cost_amount` 做毛利分析。这样避免 qingfeng 式“切换 + 重计费”的复杂度。
8. **ADR 0001 修订**：原文“模型流量只经过 Anytoken”改为“模型流量只经过受控网关层；Anytoken 为存量默认，NewAPI 允许用于视频/多渠道；网关必须无状态、不得拥有用户/额度/定价”。ADR 0001 标 Amended by 0008。

## 后果（Consequences）

- 正向：视频多渠道路由、参数差异、长任务、fallback 有明确归属；pilipala 保持产品/账本边界。
- 正向：复用 NewAPI 的渠道管理、负载均衡、任务日志；Anytoken 存量不受影响。
- 正向：产品价固定，fallback 不需要重新冻结/补差，账本简单。
- 代价：两个网关（Anytoken + NewAPI）的运维面；需要 canonical schema 与 target registry；NewAPI 若需改视频协议，维护薄 fork。
- 代价：产品价固定意味着平台承担渠道成本波动；以后要做“按渠道成本定价”另开 brief。
- 不做：不在 NewAPI 建 C 端用户/钱包/额度；不让 worker 做路由/定价；不做静默降级；不把上游原文返回 C 端；不做第二套账本；不把 GPT/图片存量迁到 NewAPI（本期）。
