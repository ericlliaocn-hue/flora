# 第一阶段冻结技术栈

本页是 **stage-1（M0–M4）** 冻结栈。新增依赖或换栈必须先改 ADR / 本页并得到确认，禁止在 PR 里「顺便」换成另一套。

## 产品与仓库

| 项 | 冻结值 |
| --- | --- |
| 品牌 | flora |
| 仓库 | https://github.com/ericlliaocn-hue/flora |
| 前端来源 | infinite-canvas 的 TypeScript fork，目录 `web/` |

## 前端（已在仓）

| 项 | 冻结值 |
| --- | --- |
| 语言 | TypeScript |
| 构建 | Vite |
| UI | React 19、React Router、Ant Design、Tailwind |
| 状态 | Zustand |
| 本地持久化 | 业务列表/图片/大 JSON 用 `localforage`；`localStorage` 只放极小配置 |
| 静态文案 | 前端 locale（先 zh-CN / en-US） |

M2 之前前端仍可能直连 OpenAI 兼容口（上游现状）。M2 托管路径改为只打 flora 业务 API。

## 业务 API（将入仓）

| 项 | 冻结值 |
| --- | --- |
| 语言 / 运行时 | Java 21 |
| 框架 | Spring Boot 3 |
| 认证 | Sa-Token |
| 主库 | PostgreSQL |
| 缓存 / 会话辅助 | Redis |
| 对象存储 | 火山引擎 TOS（Volcengine TOS） |
| API 风格 | JSON；内部任务合同见 [api/gateway-contract.md](./api/gateway-contract.md) |

不在 stage-1 用 Node/Nest、Go、Kotlin 写业务 API。不在业务 API 里再实现一套模型聚合网关。

## 模型网关

| 项 | 冻结值 |
| --- | --- |
| 唯一网关 | **Anytoken** |
| 禁止 | fork 或内嵌 NewAPI / qfzy-api |
| 禁止 | flora 与 Anytoken 双用户、双额度、双账单 |
| 浏览器 | 不持有 Anytoken 或上游供应商密钥 |

flora 拥有用户与钱包；Anytoken 只负责把模型请求转到上游。

## 队列与任务

| 项 | 冻结值 |
| --- | --- |
| 第一期队列 | RabbitMQ |
| 消息体 | 仅 `job_id`（及队列需要的最小信封，不含业务大 JSON） |
| Job 模型 | 与队列实现无关；状态存在 Postgres |
| 后续可选 | RocketMQ；换队列不改 job 状态机 |
| Worker | 瘦进程：读 job、调 Anytoken、写 step/结果、ack/重试/DLQ |

## 计费

| 项 | 冻结值 |
| --- | --- |
| 模式 | 预付费钱包 |
| 状态 | freeze（提交）→ confirm（成功）→ release（失败/取消） |
| 账本位置 | 只在 flora 业务库 |

## 国际化与错误

| 项 | 冻结值 |
| --- | --- |
| 静态 UI | 前端 locale 文件 |
| 动态运营文案 / 错误模板 | 一张 `i18n_message` 表 |
| 语言 | 先 zh + en |
| 用户错误 | 友好文案 + 稳定 `error_code` |
| 上游原文 | 只进日志与 `provider_task`，不进 C 端默认响应 |

## Admin

| 项 | 冻结值 |
| --- | --- |
| 顺序 | C 端最小闭环之后（M4 / WP11） |
| 职责 | 运维与配置 |
| 禁止 | 做成第二套创作产品 |

技术选型可复用 `web/` 的 React 栈并做路由隔离，或独立轻量后台；选定在 WP11 brief 里定，本页只冻结「它不是第二产品」。

## 明确不进 stage-1

- NewAPI / qfzy-api fork
- Kafka、云厂商延时队列作为第一期主队列（第一期就是 RabbitMQ）
- 自建 GPU / Comfy 运行时
- 第二套额度账本
- 为了「兼容更多队列」在 job 表里写死 RabbitMQ 字段
- 在 worker 里引入画布、创作编排、计费确认
- 把工程文档搬进 `docs/content/docs/`

## 现有仓库里还不是 flora 栈的部分

以下是 **上游遗留**，不是目标栈，不要在文档里写成「flora 已采用」：

- 浏览器保存 AI API Key，前端直连供应商
- 画布/素材以浏览器本地为主
- `canvas-agent/`、Codex 插件、公开 Fumadocs 站点

它们可以暂时保留。要改，走 brief，并对照本页冻结值。
