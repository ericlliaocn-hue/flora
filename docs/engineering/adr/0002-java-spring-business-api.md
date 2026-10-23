# ADR 0002 — 业务 API 用 Java 21 + Spring Boot 3

- 状态：Accepted
- 日期：2026-10-23

## 背景（Context）

前端已确定为 TypeScript（`web/`，infinite-canvas fork）。业务侧需要会话、预付费冻结、任务状态机、outbox 与对象存储签名。若用 Node 再写一套后端，能复用部分语言，但团队选择把账本与任务放在 JVM 生态：事务、连接池、现有 Java 中间件经验（Sa-Token、成熟的 MQ 客户端）更适合钱包与 outbox。

参考实现里用 Java 并不是失败原因；失败原因是服务过大。因此「用 Java」与「禁止上帝类」必须同时成立。

## 决策（Decision）

- 业务 API 进程使用 **Java 21 + Spring Boot 3**。
- 认证使用 **Sa-Token**。
- 持久化 **PostgreSQL**，缓存 **Redis**，对象存储 **火山 TOS**。
- Worker 同属 JVM（独立进程），便于复用仓储与 Anytoken 客户端，但 **进程分离**，不把执行循环塞进 API 请求线程。
- 前端保持 TypeScript，不把业务账本写回 Node。

## 后果（Consequences）

- 正向：钱包与 job 的事务边界清晰；和冻结栈其余部分一致。
- 正向：api 与 worker 可共享少量 kernel 模块（实体、状态枚举），但不可共享 canvas 或 web 代码。
- 代价：仓库将变成多语言。CI、本地启动、密钥管理都要比纯前端复杂。见 [runbooks/local-dev.md](../runbooks/local-dev.md) 的缺口。
- 代价：Minimax / agent 改 Java 时必须遵守分层，不能因为「Spring 习惯」就堆一个 `*CenterService`。
- 不在 stage-1 用 Kotlin 重写，不在 stage-1 用 Nest/Go 并行做第二业务 API。
