# pilipala 工程文档

本目录是 **pilipala**（[ericlliaocn-hue/flora](https://github.com/ericlliaocn-hue/flora)）的工程文档树。pilipala 是 infinite-canvas 的白标 fork，目标是做成可托管、预付费、画布优先的商业创作工作台。

公开用户文档（Fumadocs / Next.js）仍在 `docs/content/` 与 `docs/` 站点配置里，**不要把工程文档写进那套内容目录**。本树给人类、Minimax 与其它 AI agent 用。

## 怎么用这套文档

1. 先读 [VISION.md](./VISION.md) 和 [MILESTONES.md](./MILESTONES.md)，确认当前工作落在哪个里程碑。
2. 改架构或技术选型前，先读 [ARCHITECTURE.md](./ARCHITECTURE.md)、[TECH_STACK.md](./TECH_STACK.md) 和 [adr/](./adr/README.md)。
3. 任何超过「改文案 / 修明显 bug」的功能，先按 [feature-briefs/_TEMPLATE.md](./feature-briefs/_TEMPLATE.md) 写 brief，再交给 Minimax 或人实现。流程见 [CONTRIBUTING.md](./CONTRIBUTING.md)。
4. 实现对照 [DEVELOPMENT.md](./DEVELOPMENT.md) 的工作包（WP0–WP11），PR 对照 brief 验收，而不是对照聊天记录。

## 文档地图

| 文档 | 用途 |
| --- | --- |
| [VISION.md](./VISION.md) | 产品定位、竞品、非目标 |
| [MILESTONES.md](./MILESTONES.md) | M0–M4 目标与退出标准 |
| [DEVELOPMENT.md](./DEVELOPMENT.md) | WP0–WP11 详细计划与协作流程 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 进程、分层、Anytoken hop、时序 |
| [TECH_STACK.md](./TECH_STACK.md) | 第一阶段冻结技术栈 |
| [CONTRIBUTING.md](./CONTRIBUTING.md) | brief → PR → review |
| [adr/](./adr/README.md) | 已拍板的架构决策 |
| [data-model/](./data-model/README.md) | 表清单与概念字段 |
| [api/](./api/README.md) | 内部网关契约 |
| [feature-briefs/](./feature-briefs/README.md) | 功能说明书；Minimax 任务从这里粘贴 |
| [runbooks/](./runbooks/README.md) | 运行手册（本地启动仍有缺口） |
| [i18n-and-errors.md](./i18n-and-errors.md) | 静态文案 vs 动态错误 |
| [SECURITY.md](./SECURITY.md) | 密钥、对话安全、Secrets |

## 当前仓库事实

- 前端：`web/`，TypeScript，infinite-canvas fork。浏览器仍直连 OpenAI 兼容接口，API Key 在本地。
- 业务 API、worker、admin、Postgres、RabbitMQ、Anytoken hop **尚未入仓**。设计以本目录为准，不要按「前端直连」现状倒推目标架构。
- 管理后台排在 C 端最小闭环之后；admin 是运维/配置面，不是第二个产品。

## 给 AI agent 的硬约束

- 大改动必须先有 feature brief，禁止直接开写。
- 模型网关只用 Anytoken，禁止 fork NewAPI / qfzy-api，禁止做第二套用户/额度账本。
- Worker 保持瘦：MQ 消息体只有 `job_id`；禁止在 worker 里拉画布图或创作编排。
- 用户可见错误必须友好且带稳定 `error_code`；上游原文只进日志 / `provider_task`。
- 不要改 `docs/content/`、`docs/app/`、`docs/Dockerfile` 这套公开站点，除非任务明确要求。
