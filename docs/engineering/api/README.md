# 内部 API

pilipala C 端与 api 之间的任务合同在 [gateway-contract.md](./gateway-contract.md)。这是 **内部 DTO**，不是 Anytoken 协议，也不是 OpenAI 兼容表面。

## 原则

- web 托管模式只打 pilipala api。
- api 不把 Anytoken 请求/响应原样转给浏览器。
- 认证：Sa-Token 会话。未登录不能 submit。
- 错误：HTTP 层可有通用信封，业务失败带稳定 `error_code` + 友好 `message`。码表见 [data-model/errors.md](../data-model/errors.md)，分工见 [i18n-and-errors.md](../i18n-and-errors.md)。
- 版本：stage-1 不承诺稳定对外公开 API。字段可以随 brief 改；改合同时更新本目录。

## 将有的表面（概念）

| 表面 | 进程 | 何时 |
| --- | --- | --- |
| 认证（登录/登出/当前用户） | api | WP3 |
| Studio 顶栏 / 活动条公开配置 `GET /api/v1/studio/features` | api | Studio chrome |
| 任务 submit / query / result / cancel | api | WP4–WP7 |
| 钱包余额（只读） | api | WP6 |
| 对象读地址 | api | WP8 |
| Admin 查询/配置 | api 或独立路由 | WP11 |

路径前缀、分页、OpenAPI 文件在实现 brief 里补。本目录先锁任务合同语义。

## 不要做的合同

- `/v1/images/generations` 直接当 pilipala 账本接口（容易滑回前端直连）。
- 在 query 响应里带 `raw_upstream`。
- 让 web 传 Anytoken token。
