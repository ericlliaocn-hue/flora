# 本地开发（含已知缺口）

## 现在能跑的：前端 `web/`

上游 infinite-canvas 的本地方式仍然有效。包管理以仓库实际为准（`web/` 下常见为 bun 或 pnpm）。

```bash
git clone git@github.com:ericlliaocn-hue/flora.git
cd flora/web
# bun install && bun run dev
# 或：pnpm install && pnpm dev
```

默认端口见 `web/package.json` 的 Vite 脚本（当前为 3000）。打开后仍是「浏览器里填 OpenAI 兼容 Base URL / Key」的上游行为，直到 WP7。

Docker 只构建/运行现有前端镜像时，同样 **不是** flora 业务栈。

## 现在能跑的：后端 `api` / `worker`（WP1 骨架）

WP1 只落了**进程骨架 + 健康检查**：没有登录、没有任务、没有队列、没有钱包。启动只需要 JDK 21；**不需要** Postgres、Redis、RabbitMQ、TOS 或 Anytoken。

仓库根是 Maven 聚合父工程，两个模块是 `api/` 与 `worker/`：

```bash
git clone git@github.com:ericlliaocn-hue/flora.git
cd flora

# 构建两个模块
./mvnw -DskipTests package

# 启动 api（8080）
./mvnw -pl api spring-boot:run -Dspring-boot.run.profiles=local
```

另开一个终端启动 worker（8081）：

```bash
./mvnw -pl worker spring-boot:run -Dspring-boot.run.profiles=local
```

健康检查（两个进程各一个）：

```bash
curl -s http://127.0.0.1:8080/actuator/health   # {"status":"UP"}
curl -s http://127.0.0.1:8081/actuator/health   # {"status":"UP"}
```

端口可用 `FLORA_API_PORT` / `FLORA_WORKER_PORT` 覆盖。仓库带 Maven Wrapper，没有 Maven 也能跑；已装 Maven 时把 `./mvnw` 换成 `mvn` 等价。

配置分层：

- `application.yml` —— 默认值 + 环境变量占位，所有密钥都从这里注入
- `application-local.yml` —— 本地 profile 的**非密钥**默认值（本地网关 stub 地址、DEBUG 日志）

密钥注入（仓库里不放真实值）：

```bash
export FLORA_GATEWAY_BASE_URL=https://<anytoken-host>
export FLORA_GATEWAY_API_KEY=<your-key>
```

也可以把本地私有文件放在未入库的 `api/config/` 或 `worker/config/` 下再指过去（这两个目录已在 `.gitignore` 里）：

```bash
export SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:./api/config/flora-secrets.yml
```

## TBD：flora 业务进程（WP2 之后）

下列内容在对应 WP 落地前 **不要当作可执行步骤**：

- RabbitMQ 连接串、消费者与重试 / DLQ 策略
- 本地 Postgres / Redis / RabbitMQ 的 compose 文件（尚未作为 flora 栈提交）
- 火山 TOS bucket、STS、回调
- Anytoken 的真实调用方式（WP1 只固定了环境变量名，没接网关）
- 登录、任务提交、钱包的任何命令

## TBD：一键编排

M2 之前如果要补 `docker-compose.flora.yml`，必须在 brief 里列出服务：`web`、`api`、`worker`、`postgres`、`redis`、`rabbitmq`。TOS 与 Anytoken 通常是云服务，本地用配置指向测试实例，不要在 compose 里写真实 Key。

## 诚实对照

| 问题 | 现在 | 目标 |
| --- | --- | --- |
| flora 后端进程能起吗？ | WP1：`api` / `worker` 骨架 + `/actuator/health` | WP3+ 接任务链路 |
| 如何登录 flora 账号？ | 无 | WP3 |
| 如何提交托管任务？ | 无，前端直连供应商 | WP4–WP7 |
| 如何看 RabbitMQ 死信？ | 无 | WP4 + 以后的 runbook |
| 如何加测试余额？ | 无 | WP6 脚本 / M4 admin |

如果你发现某份对外 README 把上述目标写成「已经支持」，开 PR 改掉它，不要在本 runbook 里用假命令圆过去。
