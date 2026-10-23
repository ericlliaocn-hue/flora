# 本地开发（含已知缺口）

## 现在能跑的：前端 `web/`

上游 infinite-canvas 的本地方式仍然有效。包管理以仓库实际为准（`web/` 下常见为 bun 或 pnpm）。

```bash
git clone git@github.com:ericlliaocn-hue/flora.git
cd flora/web
# bun install && bun run dev
# 或：pnpm install && pnpm dev
```

默认端口见 `web/package.json` 的 Vite 脚本（当前为 3000）。打开后仍是「浏览器里填 OpenAI 兼容 Base URL / Key」的上游行为，直到 WP6。

Docker 只构建/运行现有前端镜像时，同样 **不是** flora 业务栈。

## TBD：flora 业务进程

下列内容在 WP1 落地前 **不要当作可执行步骤**：

- Java 21 + Spring Boot 3 的 `api/` 启动命令、profile、端口
- `worker/` 启动与 RabbitMQ 连接串
- 本地 Postgres / Redis / RabbitMQ 的 compose 文件（尚未作为 flora 栈提交）
- 火山 TOS bucket、STS、回调
- Anytoken base URL 与服务端 token 的注入方式

预期（写给 WP1 brief，不是现在的承诺）：

- 密钥只来自环境变量或未入库的 local `.env`（`.env` 必须 gitignore）
- api 与 worker 分开进程启动
- 健康检查可在无 TOS / 无 Anytoken 时启动失败要明确报缺配置，而不是半残跑任务

## TBD：一键编排

M2 之前如果要补 `docker-compose.flora.yml`，必须在 brief 里列出服务：`web`、`api`、`worker`、`postgres`、`redis`、`rabbitmq`。TOS 与 Anytoken 通常是云服务，本地用配置指向测试实例，不要在 compose 里写真实 Key。

## 诚实对照

| 问题 | 现在 | 目标 |
| --- | --- | --- |
| 如何登录 flora 账号？ | 无 | WP2 |
| 如何提交托管任务？ | 无，前端直连供应商 | WP3–WP6 |
| 如何看 RabbitMQ 死信？ | 无 | WP3 + 以后的 runbook |
| 如何加测试余额？ | 无 | WP5 脚本 / M4 admin |

如果你发现某份对外 README 把上述目标写成「已经支持」，开 PR 改掉它，不要在本 runbook 里用假命令圆过去。
