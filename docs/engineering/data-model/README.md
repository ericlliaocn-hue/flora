# 数据模型

本目录只锁 **表清单与概念字段**。列类型、索引、约束、迁移文件在对应 feature brief 里定稿后再补，不要在没有 brief 时凭经验建表。

产品未上线：表结构按新设计改即可，不必写旧字段兼容，除非 brief 明确要求。

## 表清单（stage-1）

| 表 | 所属 | 首次落地 | 说明 |
| --- | --- | --- | --- |
| `users` | 账号 | WP2 | 登录主体；Sa-Token 会话指向 `user_id` |
| `wallet` / `wallet_hold` | 计费 | WP5 | 余额 + 冻结单，见 [billing.md](./billing.md) |
| `job` | 任务 | WP3 | 状态机权威行，见 [jobs.md](./jobs.md) |
| `job_step` | 任务 | WP3 | 每次 attempt |
| `outbox_message` | 投递 | WP3 | 事务内待投递，消息体指向 `job_id` |
| `idempotency_record` | 任务 | WP3 | `user_id` + key → 已返回的 `job_id` |
| `provider_task` | 网关痕迹 | WP4 | Anytoken / 上游原文，不对 C 端默认暴露 |
| `i18n_message` | 文案 | WP8 | 动态运营/错误模板，zh+en |
| 画布/素材/生成记录 | C 端数据 | WP9 | **字段 TBD**，只要求挂 `user_id` |
| 模型展示配置 | 运营 | WP10 | **字段 TBD**，不是渠道账本 |

不建的表（非目标）：

- NewAPI 风格的 `tokens` / `quotas` / `channels` 用户额度镜像
- 按 RabbitMQ queue 名拆的业务表
- admin 专用「第二套用户」

## 权威与缓存

- 任务状态、钱包、幂等：Postgres 权威。
- Redis：会话、短锁、热点读。不把钱包余额只放在 Redis。
- TOS：字节，不把对象当行存储。
- MQ：不是存储。丢了靠 outbox + DB 状态恢复。

## 字段级细节怎么补

1. 开 brief，把「数据表字段」一节写完整。
2. Review 通过后，把稳定字段回写到本目录对应页。
3. 未回写前，实现者不得自行加「以后可能有用」的列。
