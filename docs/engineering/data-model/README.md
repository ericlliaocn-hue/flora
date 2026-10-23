# 数据模型

本目录锁 **表清单与字段级**。阶段 1（job / billing / errors / i18n / users）的列名、类型、约束、索引、枚举已由 [WP2 brief](../feature-briefs/2026-10-24-wp2-phase1-schema.md) 锁定，对应页是实现者的照抄源。

**迁移文件不在这里。** `db/migration/**` 由实现 WP 的 Flyway 落地，本目录不预写 SQL。

产品未上线：表结构按新设计改即可，不必写旧字段兼容，除非 brief 明确要求。

## 表清单（stage-1）

| 表 | 所属 | 首次落地 | 字段级 | 说明 |
| --- | --- | --- | --- | --- |
| `users` | 账号 | WP3 | [下方预留](#users仅字段预留实现-wp3) | 登录主体；Sa-Token 会话指向 `user_id` |
| `wallet` / `wallet_hold` / `wallet_ledger` | 计费 | WP6 | [billing.md](./billing.md) | 余额 + 冻结单 + 流水；单账本 |
| `job` | 任务 | WP4 | [jobs.md](./jobs.md) | 状态机权威行 |
| `job_step` | 任务 | WP4 | [jobs.md](./jobs.md) | 每次 attempt 一行 |
| `outbox_message` | 投递 | WP4 | [jobs.md](./jobs.md) | 事务内待投递，消息体指向 `job_id` |
| `idempotency_record` | 任务 | WP4 | [jobs.md](./jobs.md) | `user_id` + key → 已返回的 `job_id` |
| `provider_task` | 网关痕迹 | WP5 | [jobs.md](./jobs.md) | Anytoken / 上游原文，不对 C 端默认暴露 |
| `i18n_message` | 文案 | WP9 | [errors.md](./errors.md) | 动态运营 / 错误模板，zh + en |
| `error_code` | 错误 | — | [errors.md](./errors.md) | **不是表**，是错误码注册表（见该页） |
| 画布 / 素材 / 生成记录 | C 端数据 | WP10 | 待 WP10 brief 定 | 只要求挂 `user_id` |
| 模型展示配置 | 运营 | WP11 | 待 WP11 brief 定 | 不是渠道账本 |

不建的表（非目标）：

- NewAPI 风格的 `tokens` / `quotas` / `channels` 用户额度镜像
- 按 RabbitMQ queue 名拆的业务表（`queue_name` / `routing_key` 不进业务表，见 ADR 0003）
- Postgres `job_lease` 表（lease 等价物 = `status=running` + Redis 短锁，见 [jobs.md](./jobs.md)）
- admin 专用「第二套用户」

## 权威与缓存

- 任务状态、钱包、幂等：Postgres 权威。
- Redis：会话、短锁（worker 领取 job 的租约）、热点读。不把钱包余额只放在 Redis。
- TOS：字节，不把对象当行存储。
- MQ：不是存储。丢了靠 outbox + DB 状态恢复。

## `users`（仅字段预留，实现 WP3）

认证在 WP3 落地。本页只留最少列，**不** 设计社交登录、角色矩阵。

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `username` | TEXT | NO | UNIQUE | |
| `password_hash` | TEXT | NO | | Sa-Token 登录用；哈希算法由 WP3 brief 定 |
| `locale` | TEXT | YES | | `zh` / `en` 偏好 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

## 通用约定

- PostgreSQL；PK 默认 UUID + `gen_random_uuid()`。
- 时间一律 `TIMESTAMPTZ`。
- 金额一律 `BIGINT`（flora credit 整数）。禁止 `DECIMAL` / `FLOAT`。
- 字符串枚举存 `TEXT` + `CHECK`（或实现 WP 再用 PG enum，文档先写允许值）。
- JSON 用 `JSONB`。
- FK 指向 `users.id` 的列在 `users` 表落地前先不建物理约束，由实现 WP 决定顺序。

## 字段级怎么改

1. 实现 WP 发现本页与实现冲突 → **先改本页**（连带改 brief），再改代码。
2. 不允许在实现 PR 里顺手加「以后可能有用」的列。
3. 字段语义变更要同步 `api/gateway-contract.md` 与 [errors.md](./errors.md) 的码表。
