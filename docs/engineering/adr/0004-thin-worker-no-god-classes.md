# ADR 0004 — Worker 必须瘦，禁止上帝类

- 状态：Accepted
- 日期：2026-10-23

## 背景（Context）

qingfeng 参考实现里，`TaskCenterService` 约 3k 行，`CanvasAgentProductionService` 约 5k 行。Worker 会去拉画布与创作上下文，适配器还成双。这种结构短期能出功能，随后任何「加一个模型 / 加一种失败」都要在同一堆里改，审查无法对照 brief。

flora 的 worker 只做一件事：把已编排好的 job 执行完。画布怎么选参考图、怎么计价、怎么对用户说话，不属于执行进程。

## 决策（Decision）

Worker **可以**做：

- 消费 `{ job_id }`，加载 job，检查取消与 attempt。
- 调用唯一的 Anytoken 客户端。
- 写 `job_step`、`provider_task`（含上游原文）、TOS 对象键。
- ack、按策略重入队、进 DLQ。

Worker **不可以**做：

- 读取或更新画布文档、节点图、素材库。
- 在进程内做创作编排（多节点工作流引擎、Agent 生产大循环）。
- 直接 confirm/release 钱包或重算用户余额。
- 再写一个 NewAPI / 官方 API 适配器「备用」。
- 出现新的中心服务：单类承担任务+画布+计费+网关。

API 侧同样禁止把所有用例塞进一个 `TaskCenter`。一个用例一个应用服务。

## 后果（Consequences）

- 正向：worker 可水平扩展；画布改版不必发 worker；计费测试不必启动生成。
- 正向：Code review 有机械标准：worker 依赖里出现 `canvas` / `creation` 包即失败。
- 代价：提交前 api 必须把执行所需参数物化到 job（或 job 附属数据），不能指望 worker「自己去画布上看看」。
- 代价：多步工作流若以后要做，应拆成多个 job 或明确的 step 计划，由 api 编排，而不是复活 Production 上帝类。
- 任何把「为了方便」把画布 repository 注入 worker 的 brief，先改 brief。
