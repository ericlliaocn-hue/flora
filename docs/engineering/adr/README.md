# ADR 索引

架构决策记录（Architecture Decision Records）。编号只增不改。状态为 Accepted 的条目，实现与 brief 必须遵守；要推翻就写新的 ADR，并在本页改状态。

每条固定四段：**Context / Decision / Consequences**（文件里用中文标题「背景 / 决策 / 后果」，英文 ID 保留）。

| ID | 标题 | 状态 |
| --- | --- | --- |
| [0001](./0001-anytoken-not-newapi-fork.md) | 只用 Anytoken，不 fork NewAPI | Accepted |
| [0002](./0002-java-spring-business-api.md) | 业务 API 用 Java 21 + Spring Boot 3 | Accepted |
| [0003](./0003-rabbitmq-first-queue-agnostic-jobs.md) | 队列先用 RabbitMQ，job 模型与队列无关 | Accepted |
| [0004](./0004-thin-worker-no-god-classes.md) | Worker 必须瘦，禁止上帝类 | Accepted |
| [0005](./0005-friendly-errors-vs-raw-upstream.md) | 用户友好错误 vs 上游原文 | Accepted |
| [0006](./0006-mybatis-plus-persistence.md) | 业务库持久化采用 MyBatis-Plus（保留领域分包） | Accepted |

## 何时新写一条

- 换栈、换网关、加第二条账本、改状态机、改计费语义、换 ORM / 改持久化框架。
- 「我们以后再拆」不能替代 ADR。先写决策再写几千行服务。
