# 贡献指南（人与 Minimax）

pilipala 用 **feature brief** 当需求源，而不是用聊天记录当需求源。人与 Minimax 同一套规则。

## 开始之前

1. 读 [README.md](./README.md)、[VISION.md](./VISION.md)、[MILESTONES.md](./MILESTONES.md)。
2. 确认工作落在哪个 WP，见 [DEVELOPMENT.md](./DEVELOPMENT.md)。
3. 大改动（新表、新 API、新进程、改状态机、改计费、改品牌、改错误合同）**必须先有 brief**。
4. 小修（错别字、明显空指针、文档断链）可以不写 brief，但 PR 里仍要写清「为什么不必 brief」。

「大改动」拿不准就写 brief。写 brief 的成本低于返工一个胖 worker。

## 必须写 feature brief 的情况

- 任何 WP 的第一次实现。
- 新数据表或改计费/任务状态。
- 新的对外或内部 API。
- 换队列、换存储、加第二条模型通道（后者默认直接拒绝，见 ADR 0001）。
- 用户可见错误合同或 i18n 策略变化。
- Admin 任何功能。

Brief 模板：[feature-briefs/_TEMPLATE.md](./feature-briefs/_TEMPLATE.md)。已有示例：[WP0 白标](./feature-briefs/2026-10-24-wp0-white-label-pilipala.md)。

## 给 Minimax 的用法

1. 打开对应 brief，**整文件粘贴**，不要摘要成三句话。
2. 在粘贴内容顶部加一句：实现必须遵守 `docs/engineering/` 下 ADR 与冻结栈；超出 brief「非范围 / 非目标」的改动不要做。
3. 要求 Minimax 在 PR 描述里列出 brief 验收项的打勾结果。
4. 若 Minimax 需要更多字段级细节：停下来补 brief，而不是让它猜表结构。`data-model/` 里标了 TBD 的部分，先补 brief 再写迁移。

## 分支与 PR

- 从最新默认分支拉功能分支。
- 一个 PR 对应 brief 里的一条可验收切片，不要「顺手把 WP4–WP8 做完」。
- PR 描述最少包括：
  - 对应 brief 路径
  - 做了什么
  - 验收项对照
  - 明确没做的非范围
  - 风险（迁移、密钥、队列、计费）

## PR 检查清单

提交前作者自检，审查者按同一清单：

- [ ] 有 brief，或说明为何属于小修。
- [ ] 未引入 NewAPI / qfzy-api，未做双用户/双额度账本。
- [ ] 未把 RabbitMQ 细节写进 job 领域模型。
- [ ] Worker 没有画布/创作/钱包确认逻辑；没有新的上帝类（单文件数千行协调器）。
- [ ] C 端错误是友好文案 + `error_code`；上游原文不在默认响应里。
- [ ] 密钥不在代码、示例、聊天记录、截图里。
- [ ] 没有改无关文件，没有重写 `docs/` 公开站点。
- [ ] 静态 UI 文案走前端 locale；动态运维文案不硬编码多种语言 if-else。
- [ ] Admin 改动（若有）仍是 ops/config，没有画布创作。
- [ ] 超时/重试/大小/并发等边界值若是新行为，brief 里写了默认值与失败处理（未与用户确认过的经验值不要静默加入）。
- [ ] 持久化与文档遵守 [ADR 0006](./adr/0006-mybatis-plus-persistence.md)（MyBatis-Plus 为目标；新代码不把「禁止 ORM」当冻结栈）。
- [ ] 未把 Java 包改成全局 `controller` / `service` / `mapper`；新代码落在领域包的 `web` / `application` / `domain` / `infra`。
- [ ] 新增/变更用户行为事件时，同步更新 [data-model/analytics.md](./data-model/analytics.md) 字典。
- [ ] analytics 与埋点路径不得写入钱包（遵守 [ADR 0007](./adr/0007-analytics-events-and-reports.md)）。

## 审查怎么做

把「需求价值」和「实现质量」分开写，见根目录 `AGENTS.md` 的 PR 规则。补充 pilipala 特定判断：

- 需求价值：是否在当前里程碑、是否服务 C 端最小闭环、是否违反非目标。
- 实现质量：是否可对照 brief 验收、是否出现 qingfeng 反模式、是否破坏冻结栈。
- 需求对、实现烂：退回重做或人工按 brief 重写，不把需求一起关停。

## 文档义务

- 架构决策新增 ADR，更新 [adr/README.md](./adr/README.md)。
- 表/字段在对应 brief 定稿后，补到 `data-model/`（本阶段允许概念级，禁止默默加表却不记）。
- 用户可感知的产品行为不要写进 Fumadocs，除非有意更新公开站点；工程进度写在本树。
- 不要在公开文档里把本地画布说成已云同步。

## 安全

见 [SECURITY.md](./SECURITY.md)。任何 Key、Cookie、TOS 签名、Anytoken token 只走本地环境变量或 GitHub Secrets / vault，禁止写进 brief 示例的真实值。
