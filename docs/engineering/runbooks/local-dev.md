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

## 现在能跑的：后端 `api` / `worker`

| 进程 | 依赖 | 端口 | 健康检查 |
| --- | --- | --- | --- |
| `api` | JDK 21 + **Postgres** + **Redis** | 8080 | `GET /actuator/health` |
| `worker` | JDK 21 | 8081 | `GET /actuator/health` |

WP1 落了进程骨架与健康检查；WP3 起 `api` 需要 Postgres 与 Redis：Flyway 迁移要连 Postgres，Sa-Token 会话存 Redis。**`worker` 仍然只需要 JDK 21**，它不面向 C 端登录。

### 1. 起 Postgres 与 Redis

本机已有 Postgres / Redis 时直接用。Postgres 需要 **13 或更高**（迁移用内置的 `gen_random_uuid()`）。

macOS + Homebrew：

```bash
brew install postgresql@14
brew services start postgresql@14
# 建库建号（用你已有的超级用户执行）
createuser flora
createdb -O flora flora
psql -d flora -c "ALTER ROLE flora WITH PASSWORD 'replace-me'"

brew install redis
brew services start redis
```

或者用仓库里的最小依赖 compose（**只含 Postgres + Redis**，不是 flora 全家桶编排）：

```bash
export FLORA_DEPS_POSTGRES_PASSWORD=<本地开发用密码>
docker compose -f docker-compose.deps.yml up -d
```

compose 的宿主机端口可用 `FLORA_DEPS_POSTGRES_PORT` / `FLORA_DEPS_REDIS_PORT` 覆盖（默认 5432 / 6379）。

### 2. 环境变量

密码与 token 都不写进仓库，只走环境变量：

```bash
export FLORA_API_PORT=8080
export FLORA_TOKEN_NAME=flora-token
export FLORA_TOKEN_TIMEOUT=2592000
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/flora
export SPRING_DATASOURCE_USERNAME=flora
export SPRING_DATASOURCE_PASSWORD=replace-me
export SPRING_DATA_REDIS_HOST=127.0.0.1
export SPRING_DATA_REDIS_PORT=6379
# 若 Redis 要密码：export SPRING_DATA_REDIS_PASSWORD=replace-me
```

也可以把本地私有文件放在未入库的 `api/config/` 或 `worker/config/` 下再指过去（这两个目录已在 `.gitignore` 里）：

```bash
export SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:./api/config/flora-secrets.yml
```

### 3. 构建与启动

仓库根是 Maven 聚合父工程，两个模块是 `api/` 与 `worker/`：

```bash
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

**迁移**：`api` 启动时 Flyway 自动跑 `api/src/main/resources/db/migration/`。当前只有 `V1__create_users.sql`，只建 `users` 表；迁移历史在 `flyway_schema_history` 表里。

健康检查（两个进程各一个）：

```bash
curl -s http://127.0.0.1:8080/actuator/health   # {"status":"UP"}
curl -s http://127.0.0.1:8081/actuator/health   # {"status":"UP"}
```

端口可用 `FLORA_API_PORT` / `FLORA_WORKER_PORT` 覆盖。仓库带 Maven Wrapper，没有 Maven 也能跑；已装 Maven 时把 `./mvnw` 换成 `mvn` 等价。

配置分层：

- `application.yml` —— 默认值 + 环境变量占位，所有密钥都从这里注入
- `application-local.yml` —— 本地 profile 的**非密钥**默认值（本地网关 stub 地址、DEBUG 日志）

### 4. curl 验收（WP3 认证）

**失败时 HTTP 状态码恒为 200**：失败信号看 `ok` 字段与 `error_code`。这个约定覆盖全部失败点，WP4 的任务接口继承同一约定。

```bash
API=http://127.0.0.1:8080/api/v1/auth

# 注册：返回 user_id 与 token
curl -s -X POST $API/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password1","locale":"zh"}'
# {"ok":true,"data":{"user_id":"...","username":"alice","locale":"zh","token":"..."},"error_code":null,"message":null}

# 登录：记下 token
TOKEN=$(curl -s -X POST $API/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password1"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 带 token 读当前用户
curl -s $API/me -H "flora-token: $TOKEN"
# {"ok":true,"data":{"user_id":"...","username":"alice","locale":"zh"},"error_code":null,"message":null}

# 不带 token → UNAUTHORIZED
curl -s $API/me
# {"ok":false,"data":null,"error_code":"UNAUTHORIZED","message":"请先登录后再试。"}

# 非法用户名 / 重复用户名 / 密码过短 → VALIDATION_ERROR
curl -s -X POST $API/register -H 'Content-Type: application/json' \
  -d '{"username":"a","password":"password1"}'
# {"ok":false,"data":null,"error_code":"VALIDATION_ERROR","message":"请求参数不合法。"}

# 密码错误与「用户不存在」返回同一个码，不暴露用户是否存在
curl -s -X POST $API/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"wrongpass1"}'

# 登出后旧 token 立即失效
curl -s -X POST $API/logout -H "flora-token: $TOKEN"
curl -s $API/me -H "flora-token: $TOKEN"   # → UNAUTHORIZED
```

会话确实落在 Redis 里（键前缀 `flora-token:`）：

```bash
redis-cli --scan --pattern 'flora-token:*'
# flora-token:login:token:<uuid>
# flora-token:login:session:<user_id>
```

### 5. WP4 继承的鉴权闸门

鉴权闸门覆盖整个 `/api/v1/**`，放行名单只有 `/api/v1/auth/register` 与 `/api/v1/auth/login`；`/api/v1/jobs/**`（WP4 的 submit / query / result / cancel）自动落在同一闸门下。

当前 `/api/v1/jobs/submit` **还没有控制器**（WP4 才建），所以：

- 不带 token → `UNAUTHORIZED`（闸门先于 404 生效）
- 带有效 token → `404 Not Found`（等 WP4 补上控制器）

## TBD：flora 业务进程（WP4 之后）

下列内容在对应 WP 落地前 **不要当作可执行步骤**：

- RabbitMQ 连接串、消费者与重试 / DLQ 策略
- 本地 RabbitMQ 的 compose（Postgres / Redis 已在本页给出）
- 火山 TOS bucket、STS、回调
- Anytoken 的真实调用方式（WP1 只固定了环境变量名，没接网关）
- 任务提交、钱包的任何命令

## TBD：一键编排

M2 之前如果要补 `docker-compose.flora.yml`，必须在 brief 里列出服务：`web`、`api`、`worker`、`postgres`、`redis`、`rabbitmq`。TOS 与 Anytoken 通常是云服务，本地用配置指向测试实例，不要在 compose 里写真实 Key。

## 诚实对照

| 问题 | 现在 | 目标 |
| --- | --- | --- |
| flora 后端进程能起吗？ | WP1：`api` / `worker` 骨架 + `/actuator/health` | WP4+ 接任务链路 |
| 如何登录 flora 账号？ | WP3：`/api/v1/auth/register`、`/login`、`/logout`、`/me`，会话存 Redis | WP7 前端登录页 |
| 如何提交托管任务？ | 无，前端直连供应商 | WP4–WP7 |
| 如何看 RabbitMQ 死信？ | 无 | WP4 + 以后的 runbook |
| 如何加测试余额？ | 无 | WP6 脚本 / M4 admin |

如果你发现某份对外 README 把上述目标写成「已经支持」，开 PR 改掉它，不要在本 runbook 里用假命令圆过去。
