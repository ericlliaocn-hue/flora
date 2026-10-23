# ADR 0005 — 用户友好错误 vs 上游原文

- 状态：Accepted
- 日期：2026-10-23

## 背景（Context）

上游模型网关会返回英文堆栈、HTML、渠道名、内部模型 id、额度术语（tokens、credits）等。若原样展示，用户看不懂，也容易泄露供应商与内部路由。若完全丢掉原文，运营无法排障。qingfeng 类系统常在同一字段里混用两种受众。

flora 还要做 zh / en：静态 UI 与动态运营文案来源不同，但错误码必须稳定，不能靠「把上游 message 翻译一下」当合同。

## 决策（Decision）

- C 端默认响应只包含：**稳定 `error_code`** + **友好、已本地化的短句**（可带安全的插值，如剩余重试次数）。
- 上游原文、原始 HTTP 状态、原始 JSON 只写入 **服务端日志** 与 **`provider_task`**（或等价内部表），仅内部角色在 M4 admin 可见。
- `error_code` 由 flora 定义（例如 `WALLET_INSUFFICIENT`、`PROVIDER_UNAVAILABLE`、`JOB_CANCELLED`），不把上游 code 当作用户合同。
- 静态界面字符串放前端 locale；动态运营文案与错误模板放 **一张** `i18n_message` 表。先 zh + en。
- 禁止前端拼接供应商 `message` 作为 toast 主文案。

## 后果（Consequences）

- 正向：产品语气可控；更换 Anytoken 不会改 C 端错误合同。
- 正向：排障仍有原文，不必让用户截图英文堆栈。
- 代价：每个新失败模式都要登记码和两套文案，不能「先把 e.getMessage() 扔出去」。
- 代价：映射表会滞后于上游新错误。未识别错误统一落到 `PROVIDER_UNAVAILABLE` 或 `JOB_FAILED` 一类泛化码，原文仍进 `provider_task`。
- 安全：原文可能含请求片段，admin 展示与日志保留策略要在 WP9/WP11 brief 写清，不得进公开文档样例。
