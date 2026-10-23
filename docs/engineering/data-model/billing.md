# 计费概念模型

flora 只做 **预付费钱包**。Anytoken 侧额度是公司成本，不映射成用户账本。

字段级精度、币种、估价规则在 WP5 brief 定。本页锁语义。

## 状态：冻结 → 确认 / 释放

```
submit 估价成功 → freeze
job succeeded     → confirm（冻结转为实扣）
job cancelled / dead / 不再重试的失败 → release（解冻）
```

不允许：

- 先出图后扣款（后付费）
- 成功路径只冻不确认（余额被永久占用）
- 失败路径不释放（用户被吞钱）
- 在 Anytoken 再记一笔用户额度并与钱包对账双写

## `wallet`

| 概念字段 | 说明 |
| --- | --- |
| `user_id` | 一对一或一用户一钱包（WP5 确认） |
| `available` | 可用余额 |
| `frozen` | 已冻未确认 |
| `updated_at` | 审计 |

不变量：`available >= 0`，`frozen >= 0`。扣减只通过冻结单，不直接改两个数字「凭感觉」。

## `wallet_hold`（冻结单）

| 概念字段 | 说明 |
| --- | --- |
| `id` | 主键，job.`hold_id` 指向它 |
| `user_id` | 冗余便于对账 |
| `job_id` | 可空仅当 brief 允许先冻后建 job；默认与 job 同事务创建 |
| `amount` | 冻结额 |
| `status` | `frozen` / `confirmed` / `released` |
| `created_at` / `finalized_at` | 审计 |

终态只可进入一次。重放 confirm/release 必须幂等。

## 与任务的绑定

- 冻不住：不入队。用户得到 `WALLET_INSUFFICIENT`（名称以 WP8 码表为准）。
- 重试 attempt **不** 重复冻结。一次 job 一次 hold。
- 估价过低导致上游实际更贵：stage-1 不自动补扣，除非 WP5 brief 另写；默认「产品按标价冻，成本差算运营」。
- 估价过高：成功仍按冻结额 confirm，或按实际确认差额 release——必须在 WP5 二选一，禁止实现者现场发挥。

## 充值

M2 可以用运维脚本/SQL 加 `available`（记一笔 ledger 更好，字段 TBD）。C 端自助充值与支付渠道不是 M2 退出条件。M4 admin 提供有限加额。

## 概念流水（建议，字段 TBD）

若 WP5 需要审计表 `wallet_ledger`：

| 概念字段 | 说明 |
| --- | --- |
| `wallet_id` / `user_id` | 归属 |
| `type` | `topup` / `freeze` / `confirm` / `release` |
| `amount` | 正负约定在 brief 写死 |
| `hold_id` / `job_id` | 追溯 |
| `created_at` | 审计 |

没有流水表也可以先靠 `wallet_hold` 撑过 M2，但 admin 对账会更痛。是否建表由 WP5 brief 决定，不在本页假装已经有完整财务系统。
