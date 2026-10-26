# 工程安全

本文面向 pilipala 实现与协作，不是漏洞赏金页。上游公开安全说明仍见仓库根目录 `SECURITY.md`（infinite-canvas 策略）。**不要把真实密钥贴进聊天、brief、PR、截图或仓库。**

## 密钥放哪里

| 密钥 | 允许 | 禁止 |
| --- | --- | --- |
| Anytoken token | 服务器环境变量；GitHub Actions Secrets；团队 vault | 前端 `VITE_*`、localStorage、示例 JSON |
| 上游模型 Key | 只存在 Anytoken 侧。pilipala 进程不需要也不该存用户级上游 Key | 再做一个「渠道 Key 表」给 C 端用 |
| TOS AK/SK、签名密钥 | api/worker 环境变量或云角色 | 提交到 git；发到浏览器长期有效的 AK |
| Postgres / Redis / RabbitMQ 密码 | 环境变量 / Secrets | compose 样例里写生产密码 |
| Sa-Token 或 cookie 签名 | 环境变量 | 默认 `123456` 进生产 |
| 短信 OTP（短信宝 / 阿里云） | `PILIPALA_SMS_SMSBAO_*` 或 `PILIPALA_SMS_ALIYUN_*`；`PILIPALA_SMS_PROVIDER` 选型 | 仓库、brief、runbook 只写占位；`logging` 仅供本地 |
| 邮件 OTP（SMTP） | `PILIPALA_MAIL_*` | 禁止把 SMTP 密码写进 compose 样例 |

本地文件用 `.env`（gitignore）。文档示例只写 `ANYTOKEN_TOKEN=replace-me`。

## 托管模式的浏览器

M2 之后正式路径：

- 浏览器只有 pilipala 会话，没有 Anytoken，没有供应商 Key。
- TOS 只下发短 TTL 读地址，不把 bucket 配成长期 public + 可枚举。
- 用户不能通过 query 读到别人的 `job_id` 结果（必须校验 `user_id`）。

在 WP7 完成前，上游「前端直连 + 本地 Key」仍然存在。那是 fork 遗留，不是 pilipala 安全模型。不要在对外文档把遗留写成推荐生产架构。

## 协作时

- 给 Minimax / 其它 agent 的 brief 里禁止粘贴真实 token。排障用脱敏日志。
- PR 不得包含 `.env`、密钥 JSON、生产库 dump。
- `provider_task.raw_body` 可能含 prompt 与内部路由，仅内部角色可见；不要往公开 issue 贴全文。
- 发现密钥进了 git：轮换密钥，并从历史中移除（按平台文档），不要只在下一笔 commit 删行。

## Admin（M4）

- Admin 与 C 端权限分离。
- 原文、加额、用户查询走审计日志（字段在 WP11 brief 定）。
- Admin 不是可以任意执行生成的第二前端。

## 依赖与插件

上游画布仍允许远程节点插件在浏览器执行。托管 pilipala 若保留插件，必须在独立 brief 里写信任模型；默认不要把「用户贴 URL 装插件」当成企业默认打开项。

## 上报

对外漏洞仍按根目录 `SECURITY.md` 的渠道。内部误提交密钥：先轮换，再通知仓库管理员，不要在公开 issue 贴密钥值。
