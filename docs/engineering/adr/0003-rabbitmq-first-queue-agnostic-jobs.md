# ADR 0003 — 队列先用 RabbitMQ，job 模型与队列无关

- 状态：Accepted
- 日期：2026-10-23

## 背景（Context）

异步生成必须入队：重试、并发、DLQ、取消都要求「状态在数据库，投递可失败可重放」。有的方案把 queue 名、routing key、tag、offset 写进业务表，换 RocketMQ / 云厂商队列时要做领域迁移。qingfeng 类系统还常把「发送消息」和「改状态」散落在几千行服务里，难以证明只投递了一次。

flora 后续可能换 RocketMQ，但不想改 `created/queued/running/...` 这张状态机。

## 决策（Decision）

- 第一期传输实现使用 **RabbitMQ**。
- **Job 领域模型与队列无关**：表内存状态、attempt、关联钱包冻结，不存 AMQP 协议字段。
- 消息体只有 **`job_id`**（外加传输层最小信封：如 event_id 用于投递幂等）。业务参数以 Postgres 为准。
- 使用 **outbox**：创建/入队在同一事务写入 `outbox_message`，再异步投递，避免「库成功、MQ 失败」或反过来。
- 以后换 RocketMQ 只换 Transport + outbox dispatcher，不改状态枚举与 worker 的「按 id 加载」合同。

## 后果（Consequences）

- 正向：换队列是基础设施 PR，不是领域重构。
- 正向：worker 崩溃后可按 `job_id` 重放；消息丢了可以靠 outbox 再投。
- 代价：不能把大参数、图片 base64 塞进 MQ。大对象走 TOS，参数走 job 行或附属表。
- 代价：第一期要认真做 DLQ 与重试间隔；这些是 Transport 配置，在 brief 里写默认值，不得在代码里静默写「经验值」。
- 禁止：在 `job` 上增加 `queue_name` / `routing_key` 作为业务必填字段。
