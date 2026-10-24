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
| `api` | JDK 21 + **Postgres** + **Redis** + **RabbitMQ** | 8080 | `GET /actuator/health` |
| `worker` | JDK 21 + **Postgres** + **Redis** + **RabbitMQ** | 8081 | `GET /actuator/health` |

WP1 落了进程骨架与健康检查；WP3 起 `api` 需要 Postgres 与 Redis（Flyway 迁移要连 Postgres，Sa-Token 会话存 Redis）；WP4 起 `api` 还要 RabbitMQ（outbox dispatcher 要把 pending 消息投递出去）。

WP5 起 `worker` 也需要 Postgres + Redis + RabbitMQ：它直连 Postgres 做 claim / complete 两段短事务，用 Redis 短锁 `job:lease:{job_id}` 防同一个 job 被两个 worker 并发跑，并监听 `flora.jobs.execute` 消费。

> broker 没起时，两个进程的 `/actuator/health` 都会因为 RabbitMQ 健康探针而报 **DOWN**（HTTP 503）。这是已知且预期的：WP4 之后 `api` 真的需要 broker，WP5 之后 `worker` 也真的需要 broker。

### 1. 起 Postgres、Redis 与 RabbitMQ

本机已有 Postgres / Redis / RabbitMQ 时直接用。Postgres 需要 **13 或更高**（迁移用内置的 `gen_random_uuid()`）。

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

brew install rabbitmq
brew services start rabbitmq
```

RabbitMQ 第一次启动会建 `guest/guest`（只允许本机连）。管理台在 <http://127.0.0.1:15672>。

或者用仓库里的最小依赖 compose（**只含 Postgres + Redis + RabbitMQ**，不是 flora 全家桶编排）：

```bash
export FLORA_DEPS_POSTGRES_PASSWORD=<本地开发用密码>
docker compose -f docker-compose.deps.yml up -d
```

compose 的宿主机端口可用 `FLORA_DEPS_POSTGRES_PORT` / `FLORA_DEPS_REDIS_PORT` / `FLORA_DEPS_RABBITMQ_PORT` 覆盖（默认 5432 / 6379 / 5672）。

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

# WP4：RabbitMQ（本地默认 guest/guest，只在本机用）
export SPRING_RABBITMQ_HOST=127.0.0.1
export SPRING_RABBITMQ_PORT=5672
export SPRING_RABBITMQ_USERNAME=guest
export SPRING_RABBITMQ_PASSWORD=guest

# WP4：Job 域（队列名只是传输配置，不是 job 表的列）
export FLORA_JOB_TRANSPORT=rabbit          # rabbit | fake（fake = 内存假 transport，本地无 broker 演练用）
export FLORA_JOB_EXCHANGE=flora.jobs
export FLORA_JOB_ROUTING_KEY=job.execute
export FLORA_JOB_QUEUE=flora.jobs.execute
export FLORA_JOB_DEAD_LETTER_QUEUE=flora.jobs.execute.dlq
export FLORA_JOB_LEASE_TTL_SECONDS=60      # worker 领取 job 的 Redis 短锁 TTL
export FLORA_OUTBOX_POLL_MS=500            # outbox 轮询间隔
export FLORA_OUTBOX_BATCH_SIZE=50          # 单轮最多投递多少条
export FLORA_OUTBOX_MAX_ATTEMPTS=10        # 同一条消息连续失败多少次后停在 failed

# WP5：worker 与唯一 Anytoken 网关
export FLORA_WORKER_PORT=8081
export FLORA_WORKER_PREFETCH=1             # 一次只预取一条
export FLORA_GATEWAY_MODE=stub              # stub（本地假上游，默认）| http（真实 Anytoken）
export FLORA_GATEWAY_BASE_URL=http://127.0.0.1:18080   # 仅 http 模式生效
export FLORA_GATEWAY_API_KEY=               # 仅 http 模式生效；由环境变量注入，勿入库
export FLORA_GATEWAY_TIMEOUT_MS=30000
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

**迁移**：`api` 启动时 Flyway 自动跑 `api/src/main/resources/db/migration/`。当前有：

- `V1__create_users.sql` —— 只建 `users` 表
- `V2__create_job_domain.sql` —— 建 `job` / `job_step` / `outbox_message` / `idempotency_record`
- `V3__create_provider_task.sql` —— 建 `provider_task`，并给 `job_step.provider_task_id` 补上物理外键

迁移历史在 `flyway_schema_history` 表里。**迁移统一由 api 管理**，`worker` 不跑 Flyway（`spring.flyway.enabled: false`）。

健康检查（两个进程各一个）：

```bash
curl -s http://127.0.0.1:8080/actuator/health   # {"status":"UP"}（需要 RabbitMQ 在线）
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

### 5. curl 验收（WP4 任务接口）

任务接口全部要登录（WP3 的鉴权闸门覆盖整个 `/api/v1/**`）。

```bash
API=http://127.0.0.1:8080/api/v1
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password1"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# submit：写 job(queued) + outbox(pending) + 幂等记录
curl -s -X POST $API/jobs/submit -H "flora-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"k1","capability":"image.generate","model":"demo","params":{"prompt":"hi"}}'
# {"ok":true,"data":{"job_id":"...","status":"queued","hold_id":null},"error_code":null,"message":null}

# 同一个 idempotency_key + 同一个 body → 返回同一个 job_id，不二次入队
curl -s -X POST $API/jobs/submit -H "flora-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"k1","capability":"image.generate","model":"demo","params":{"prompt":"hi"}}'

# 同一个 key + 不同的 body → IDEMPOTENCY_CONFLICT
curl -s -X POST $API/jobs/submit -H "flora-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"k1","capability":"image.generate","model":"demo","params":{"prompt":"别的"}}'
# {"ok":false,"data":null,"error_code":"IDEMPOTENCY_CONFLICT","message":"同一个幂等键对应了不同的请求内容。"}

# query
curl -s $API/jobs/$JOB_ID -H "flora-token: $TOKEN"
# {"ok":true,"data":{"job_id":"...","status":"queued","attempt":0,"max_attempts":3,"error_code":null,"message":null,"updated_at":"..."}}

# result（未成功时 outputs 为空）
curl -s $API/jobs/$JOB_ID/result -H "flora-token: $TOKEN"

# cancel：queued 直接变 cancelled，终态幂等
curl -s -X POST $API/jobs/$JOB_ID/cancel -H "flora-token: $TOKEN"
# {"ok":true,"data":{"job_id":"...","status":"cancelled","attempt":0,"max_attempts":3,"error_code":"JOB_CANCELLED","message":"任务已取消。", ...}}

# 别人的 job / 不存在的 job → JOB_NOT_FOUND（不泄露存在性）
curl -s $API/jobs/00000000-0000-0000-0000-000000000000 -H "flora-token: $TOKEN"
# {"ok":false,"data":null,"error_code":"JOB_NOT_FOUND","message":"任务不存在。"}

# 不带 token → UNAUTHORIZED
curl -s -X POST $API/jobs/submit -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"k2","capability":"image.generate","model":"demo","params":{}}'
# {"ok":false,"data":null,"error_code":"UNAUTHORIZED","message":"请先登录后再试。"}
```

库里能看到 outbox 被投出去（`status` 从 `pending` 变 `sent`）：

```bash
psql -d flora -c "SELECT id, aggregate_id, payload, status, sent_at FROM outbox_message ORDER BY created_at DESC LIMIT 5;"
```

消息体只有 `job_id` 与传输层 `event_id`，没有 params、没有图片、没有钱包额（ADR 0003）：

```json
{"job_id": "222ffe85-5f38-4abb-8694-4ea7702abe85", "event_id": "162a51ce-faf9-49f0-ba68-61a7ee3ae179"}
```

RabbitMQ 侧能看到 exchange / queue / 死信队列：

```bash
rabbitmqctl list_queues name messages consumers
# flora.jobs.execute        1  0
# flora.jobs.execute.dlq    0  0
```

### 6. WP5 双进程端到端（api + worker）

**要同时起 api（8080）与 worker（8081）**，worker 才会把队列里的 job 领走。默认 `FLORA_GATEWAY_MODE=stub`：不发外部请求，用本地确定性假上游跑通全链路。

```bash
API=http://127.0.0.1:8080/api/v1
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password1"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 1) 正常任务（stub 默认成功）：submit 后被 worker 消费 → succeeded
JOB_OK=$(curl -s -X POST $API/jobs/submit -H "flora-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp5-ok-1","capability":"image.generate","model":"demo","params":{"prompt":"a quiet garden"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 1
curl -s $API/jobs/$JOB_OK -H "flora-token: $TOKEN"
# → status="succeeded", attempt=1, error_code=null
curl -s $API/jobs/$JOB_OK/result -H "flora-token: $TOKEN"
# → status="succeeded", outputs=[{"type":"image","object_key":"anytoken/img/...","url":null}]

# 2) 上游暂时不可用（stub_outcome=provider_unavailable）：自动重试 3 次后进 dead + 死信队列
JOB_RETRY=$(curl -s -X POST $API/jobs/submit -H "flora-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp5-retry-1","capability":"image.generate","model":"demo","params":{"prompt":"fail","stub_outcome":"provider_unavailable"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 3
curl -s $API/jobs/$JOB_RETRY -H "flora-token: $TOKEN"
# → status="dead", attempt=3, max_attempts=3, error_code="PROVIDER_UNAVAILABLE", message="生成服务暂时不可用，请稍后重试。"

# 3) 不可重试业务失败（stub_outcome=job_failed）：attempt=1 直接进 dead
JOB_FAIL=$(curl -s -X POST $API/jobs/submit -H "flora-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp5-fail-1","capability":"image.generate","model":"demo","params":{"prompt":"bad","stub_outcome":"job_failed"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 1
curl -s $API/jobs/$JOB_FAIL -H "flora-token: $TOKEN"
# → status="dead", attempt=1, error_code="JOB_FAILED", message="任务执行失败。"

# 4) 运行中取消：stub_delay_ms 让上游慢一点，取消意图优先于本次 attempt 的结果
JOB_CANCEL=$(curl -s -X POST $API/jobs/submit -H "flora-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp5-cancel-1","capability":"image.generate","model":"demo","params":{"prompt":"slow","stub_delay_ms":4000}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 2
curl -s -X POST $API/jobs/$JOB_CANCEL/cancel -H "flora-token: $TOKEN"   # 运行中取消：先记意图
sleep 4
curl -s $API/jobs/$JOB_CANCEL -H "flora-token: $TOKEN"
# → status="cancelled", error_code="JOB_CANCELLED"（即使上游这次成功也收敛为 cancelled）
```

上游原文只进 `provider_task`，`job_step` 通过 `provider_task_id` 关联：

```bash
psql -d flora -c "SELECT job_id, job_step_id, upstream_request_id, raw_status, raw_body FROM provider_task ORDER BY created_at DESC LIMIT 5;"
psql -d flora -c "SELECT job_id, attempt, status, error_code, provider_task_id FROM job_step ORDER BY started_at DESC LIMIT 5;"
```

死信队列里能看到 dead 的消息：

```bash
rabbitmqctl list_queues name messages consumers
# flora.jobs.execute        0  1
# flora.jobs.execute.dlq    2  0
```

Redis 短锁在消费期间是真实存在的（跑完就释放）：

```bash
redis-cli --scan --pattern 'job:lease:*'
# job:lease:<job_id>
```

### 7. WP5 的边界（不要误以为已经能用）

- **没有钱包**：submit 不 freeze，`hold_id` 恒为 null。钱包整包在 WP6。
- **默认不连真上游**：`FLORA_GATEWAY_MODE=stub` 是本地假上游；`http` 模式才真的调 Anytoken，
  而且本 WP 没有接真实付费账号与账单。
- **生成结果是假的**：WP5 还没接 TOS，`result[].url` 恒为 null，只有合成的 `object_key`。
  **不要把「submit 能跑通」写成「托管 flora 已可用」**。
- **没有 DLQ 重投运营台**：死信只进队列，没有 UI（WP11）。
- **没有超时孤儿回收**：running 状态卡住的任务不会被定时扫回 queued（属运维策略，不在本 WP）。

## TBD：flora 业务进程（WP5 之后）

下列内容在对应 WP 落地前 **不要当作可执行步骤**：

- 火山 TOS bucket、STS、回调
- Anytoken 的真实付费调用方式（WP5 只有适配器边界 + 本地 stub）
- 钱包的 freeze / confirm / release（WP6）
- 前端改打 flora API（WP7）

## TBD：一键编排

M2 之前如果要补 `docker-compose.flora.yml`，必须在 brief 里列出服务：`web`、`api`、`worker`、`postgres`、`redis`、`rabbitmq`。TOS 与 Anytoken 通常是云服务，本地用配置指向测试实例，不要在 compose 里写真实 Key。

## 诚实对照

| 问题 | 现在 | 目标 |
| --- | --- | --- |
| flora 后端进程能起吗？ | WP1：`api` / `worker` 骨架 + `/actuator/health` | WP4+ 接任务链路 |
| 如何登录 flora 账号？ | WP3：`/api/v1/auth/register`、`/login`、`/logout`、`/me`，会话存 Redis | WP7 前端登录页 |
| 如何提交托管任务？ | WP5：submit → worker 消费 → succeeded / dead 闭环跑通，**但没有钱包、没有真上游、没有 TOS** | WP4–WP7 |
| 如何看 RabbitMQ 死信？ | WP5：dead 的消息会进 `flora.jobs.execute.dlq`，**但没有重投运营台** | WP4 + 以后的 runbook |
| 如何加测试余额？ | 无 | WP6 脚本 / M4 admin |

如果你发现某份对外 README 把上述目标写成「已经支持」，开 PR 改掉它，不要在本 runbook 里用假命令圆过去。
