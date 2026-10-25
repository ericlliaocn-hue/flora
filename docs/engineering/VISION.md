# pilipala 愿景

## 产品是什么

**pilipala** 是面向托管运营的无限画布创作工作台。它从 [infinite-canvas](https://github.com/basketikun/infinite-canvas) fork，保留画布编排与多模态创作体验，补上账号、预付费、任务、对象存储与统一模型网关，让团队可以自己运营，而不是让每个用户在浏览器里填 API Key。

仓库：[https://github.com/ericlliaocn-hue/pilipala](https://github.com/ericlliaocn-hue/pilipala)

品牌名固定为 **pilipala**（小写）。用户可见文案、标题、logo 与仓库身份都走 pilipala，不再以「无限画布 / infinite-canvas」作为产品名对外。上游项目名称只出现在版权、fork 说明与工程对照里。

## 给谁用

- **创作者（C 端）**：在一张画布上连续生图、改图、排参考、沉淀素材；不必自己申请一堆模型站、不必在前端保存供应商密钥。
- **运营方**：用预付费钱包控制成本，用任务状态机与死信队列看清失败，用 Anytoken 换模型而不改业务账本。
- **内部实现者（人 / Minimax）**：按 brief 交付，按 ADR 守边界，避免再做出 qingfeng 那种上帝服务。

第一阶段只做 **C 端最小托管闭环**。管理后台是运营工具，排在 C 端能用之后。

## 和上游 infinite-canvas 的关系

| | infinite-canvas | pilipala |
| --- | --- | --- |
| 定位 | 开源本地工作台 | 白标托管产品 |
| 密钥 | 浏览器本地，前端直连供应商 | 服务端持有；浏览器只打 pilipala API |
| 计费 | 无 | 预付费：冻结 → 成功确认 / 失败释放 |
| 任务 | 页面内请求 | `job` + `job_step` + 与队列无关的状态机 |
| 存储 | IndexedDB / localforage 为主 | 账号数据在 Postgres；结果对象进火山 TOS |
| 文档 | `docs/content/` 用户文档 | 另加 `docs/engineering/` 工程文档 |

fork 不是为了再维护一份开源社区版。上游 README、赞助墙、公开文档站点暂时保留，便于对照；用户可见品牌由 [WP0](./feature-briefs/2026-10-24-wp0-white-label-pilipala.md) 替换。

## 竞品对照

pilipala 不追求「什么都能做」，而是 **画布工作台 + 自有账本 + 薄网关 hop**。

### LibTV（哩布等社区型创作平台）

- 强项：内容社区、模板/作品分发、偏消费与发现。
- pilipala 不跟：不做社区 feed、不做作品广场、不做达人生态。
- pilipala 要赢：同一张画布上的连续创作与可审计的预付费任务，而不是内容分发。

### RunningHub

- 强项：云端 ComfyUI / 节点工作流、偏专业流水线与自定义图。
- pilipala 不跟：不自建 Comfy 运行时，不把画布做成通用工作流 IDE。
- pilipala 要赢：创作者先获得「选节点 → 出图 → 再编辑」的低摩擦体验；复杂工作流不是 M0–M4 目标。

### TapNow

- 强项：更偏消费级 AI 创作入口，产品完整、运营重。
- pilipala 不跟：不在第一阶段铺齐聊天/社区/营销增长闭环。
- pilipala 要赢：画布是一等公民；模型供给外包给 Anytoken，自己只拥有用户、钱包、任务与画布数据。

一句话：**竞品拥有流量或工作流运行时；pilipala 拥有画布会话、任务账本和预付费。**

## 原则

1. **账本只有一套。** 用户、额度、冻结/确认/释放只存在 pilipala 业务库。Anytoken 是模型网关，不是第二套用户/配额系统。
2. **网关只 hop，不 fork。** 不 fork NewAPI / qfzy-api。供应商差异消化在 Anytoken，业务侧只认内部 DTO。
3. **任务与队列解耦。** 状态机在 Postgres；MQ 消息体只有 `job_id`。第一期用 RabbitMQ，以后可换 RocketMQ，不必改 job 表。
4. **Worker 必须瘦。** Worker 读 job、调 Anytoken、写 step/结果、ack/重试/DLQ。画布拓扑、提示词拼装、计费确认不进 worker。
5. **错误对人友好。** 用户看到稳定 `error_code` + 本地化短句；上游原文只进日志和 `provider_task`。
6. **Admin 不是第二产品。** 配置模型、查任务、调钱包。不做另一套创作台。

## 非目标（Non-goals）

- 不 fork、不内嵌、不双写 NewAPI / qfzy-api。
- 不在 Anytoken 与 pilipala 之间做双用户、双额度、双账单。
- 不把管理后台做成第二个 C 端（没有画布创作、没有独立增长指标）。
- 不在 M0–M4 做社区、作品广场、社交关系、公开模型市场。
- 不自建 GPU / ComfyUI 集群。
- 不把当前浏览器本地画布/素材说成「已支持云同步」——云端持久化是后续工作包，未落地前文档必须诚实。
- 不重写 `docs/` 下的 Fumadocs 公开站点来承载工程文档。
- 不在第一阶段同时支持「浏览器直连供应商」与「pilipala 托管」两套计费（托管模式以服务端为准；本地开源用法不是 pilipala 商业路径）。

## 成功长什么样

一个预付费用户打开 pilipala 画布，提交一次生图：钱包先冻结，任务进入 `created → queued → running → succeeded`，结果进 TOS，前端用友好文案或稳定错误码展示结局。运营可以用 admin（M4）看到任务与配置，但不能也不会在 admin 里再做一张创作画布。
