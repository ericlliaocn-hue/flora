# 国际化与错误

两套文案来源，不要混用。错误对用户和对运营也拆开。决策见 [ADR 0005](./adr/0005-friendly-errors-vs-raw-upstream.md)。

## 静态 UI：前端 locale

- 文件：`web/src/i18n/locales/zh-CN.ts`、`en-US.ts`（以及后续 locale）。
- 适用：导航、按钮、空状态、设置项、画布固定说明。
- 语言切换只影响这些键。不要在组件里写 `if (zh) ... else ...` 的整段文案。
- WP0 之后产品名键是 flora，不要把品牌写回「无限画布」。

## 动态运营文案：一张 `i18n_message`

适用：

- 错误模板（可带安全插值）
- 运营可改的公告、模型下架说明、充值提示
- 任何不发前端版也想改的句子

列级 schema 已由 [WP2 brief](./feature-briefs/2026-10-24-wp2-phase1-schema.md) 锁定，见 [data-model/errors.md](./data-model/errors.md#i18n_message-列级-schema)：

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | |
| `key` | TEXT | NO | UNIQUE (`key`, `locale`) | 稳定键，常与 `error_code` 相同 |
| `locale` | TEXT | NO | CHECK ∈ `zh` \| `en` | stage-1 先这两种 |
| `template` | TEXT | NO | | 短句，插值用有限占位符 |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | 审计 |

不要为每种语言建一张表，不要在 Java 里 `if ("zh")` 拼错误。

## 错误合同

C 端默认永远是：

| 字段 | 受众 | 内容 |
| --- | --- | --- |
| `error_code` | 用户 + 客服 | 稳定大写蛇形，flora 定义 |
| `message` | 用户 | 当前 locale 的友好短句 |
| 日志 / `provider_task.raw_body` | 运营 | 上游原文 |

预留码由 [WP2 brief](./feature-briefs/2026-10-24-wp2-phase1-schema.md) 锁死名称，完整注册表见 [data-model/errors.md](./data-model/errors.md)：

| code | 何时 |
| --- | --- |
| `WALLET_INSUFFICIENT` | 冻结失败 |
| `JOB_NOT_FOUND` | 查询了别人的或不存在的 job |
| `JOB_CANCELLED` | 取消 |
| `JOB_FAILED` | 已映射但仍泛化的业务失败 |
| `PROVIDER_UNAVAILABLE` | Anytoken 或上游不可用 / 未识别 |
| `IDEMPOTENCY_CONFLICT` | 同一 key 不同 body |
| `UNAUTHORIZED` | 无会话 |
| `VALIDATION_ERROR` | 请求字段不合法 |

未识别的上游错误 → 泛化码 + 原文入库，禁止把 `e.getMessage()` 当 `message`。

## 语言选择

1. 已登录用户若有 locale 偏好，用偏好。
2. 否则用请求 `Accept-Language` 或前端当前 i18n 语言。
3. 缺词条回退：请求语言 → `zh` → `error_code` 本身（最后手段，仍不要回退到上游英文堆栈）。

## 前端职责

- toast / 节点失败态显示 `message`，可附 `error_code`。
- 禁止 `error.response.data.upstream` 或 axios 拦截器把整段 HTML 弹出来。
- 静态「生成失败」和动态码可以同时存在：固定壳子走 locale，具体原因走接口 `message`。
