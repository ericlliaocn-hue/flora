# 本地开发（含已知缺口）

## 现在能跑的：前端 `web/`

上游 infinite-canvas 的本地方式仍然有效。包管理以仓库实际为准（`web/` 下常见为 bun 或 pnpm）。

```bash
git clone git@github.com:ericlliaocn-hue/pilipala.git
cd pilipala/web
# bun install && bun run dev
# 或：pnpm install && pnpm dev
```

默认端口见 `web/package.json` 的 Vite 脚本（当前为 3000）。

WP7 起前端默认是 **托管云端模式**（`channelMode: "remote"`）：生图不再直连任何模型供应商，
而是走 pilipala 自己的 `api`（登录 → 钱包 → `submit` → 轮询 → `result`）。本地联调要同时起 `api`、
`worker` 与本地 TOS 文件桶，见第 11 节。想退回上游那种「浏览器里填 Base URL / Key」的老行为，
在「配置 → 渠道」里把模式切回 **本地直连**。

Vite dev server 会把 `/api` 代理到 `http://localhost:8080`（用 `VITE_API_PROXY_TARGET` 可改）；
前端要打到别处时设 `VITE_API_BASE_URL`（例如 `http://127.0.0.1:8080`）。

Docker 只构建/运行现有前端镜像时，同样 **不是** pilipala 业务栈。

## 现在能跑的：后端 `api` / `worker`

| 进程 | 依赖 | 端口 | 健康检查 |
| --- | --- | --- | --- |
| `api` | JDK 21 + **Postgres** + **Redis** + **RabbitMQ** | 8080 | `GET /actuator/health` |
| `worker` | JDK 21 + **Postgres** + **Redis** + **RabbitMQ** | 8081 | `GET /actuator/health` |

WP1 落了进程骨架与健康检查；WP3 起 `api` 需要 Postgres 与 Redis（Flyway 迁移要连 Postgres，Sa-Token 会话存 Redis）；WP4 起 `api` 还要 RabbitMQ（outbox dispatcher 要把 pending 消息投递出去）。

WP5 起 `worker` 也需要 Postgres + Redis + RabbitMQ：它直连 Postgres 做 claim / complete 两段短事务，用 Redis 短锁 `job:lease:{job_id}` 防同一个 job 被两个 worker 并发跑，并监听 `pilipala.jobs.execute` 消费。

> broker 没起时，两个进程的 `/actuator/health` 都会因为 RabbitMQ 健康探针而报 **DOWN**（HTTP 503）。这是已知且预期的：WP4 之后 `api` 真的需要 broker，WP5 之后 `worker` 也真的需要 broker。

### 1. 起 Postgres、Redis 与 RabbitMQ

本机已有 Postgres / Redis / RabbitMQ 时直接用。Postgres 需要 **13 或更高**（迁移用内置的 `gen_random_uuid()`）。

macOS + Homebrew：

```bash
brew install postgresql@14
brew services start postgresql@14
# 建库建号（用你已有的超级用户执行）
createuser pilipala
createdb -O pilipala pilipala
psql -d pilipala -c "ALTER ROLE pilipala WITH PASSWORD 'replace-me'"

brew install redis
brew services start redis

brew install rabbitmq
brew services start rabbitmq
```

RabbitMQ 第一次启动会建 `guest/guest`（只允许本机连）。管理台在 <http://127.0.0.1:15672>。

或者用仓库里的最小依赖 compose（**只含 Postgres + Redis + RabbitMQ**，不是 pilipala 全家桶编排）：

```bash
export PILIPALA_DEPS_POSTGRES_PASSWORD=<本地开发用密码>
docker compose -f docker-compose.deps.yml up -d
```

compose 的宿主机端口可用 `PILIPALA_DEPS_POSTGRES_PORT` / `PILIPALA_DEPS_REDIS_PORT` / `PILIPALA_DEPS_RABBITMQ_PORT` 覆盖（默认 5432 / 6379 / 5672）。

### 2. 环境变量

密码与 token 都不写进仓库，只走环境变量：

```bash
export PILIPALA_API_PORT=8080
export PILIPALA_TOKEN_NAME=pilipala-token
export PILIPALA_TOKEN_TIMEOUT=2592000
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/pilipala
export SPRING_DATASOURCE_USERNAME=pilipala
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
export PILIPALA_JOB_TRANSPORT=rabbit          # rabbit | fake（fake = 内存假 transport，本地无 broker 演练用）
export PILIPALA_JOB_EXCHANGE=pilipala.jobs
export PILIPALA_JOB_ROUTING_KEY=job.execute
export PILIPALA_JOB_QUEUE=pilipala.jobs.execute
export PILIPALA_JOB_DEAD_LETTER_QUEUE=pilipala.jobs.execute.dlq
export PILIPALA_JOB_LEASE_TTL_SECONDS=60      # worker 领取 job 的 Redis 短锁 TTL
export PILIPALA_OUTBOX_POLL_MS=500            # outbox 轮询间隔
export PILIPALA_OUTBOX_BATCH_SIZE=50          # 单轮最多投递多少条
export PILIPALA_OUTBOX_MAX_ATTEMPTS=10        # 同一条消息连续失败多少次后停在 failed

# WP5：worker 与唯一 Anytoken 网关
export PILIPALA_WORKER_PORT=8081
export PILIPALA_WORKER_PREFETCH=1             # 一次只预取一条
export PILIPALA_GATEWAY_MODE=stub              # stub（本地假上游，默认）| http（真实 Anytoken）
export PILIPALA_GATEWAY_BASE_URL=http://127.0.0.1:18080   # 仅 http 模式生效
export PILIPALA_GATEWAY_API_KEY=               # 仅 http 模式生效；由环境变量注入，勿入库
export PILIPALA_GATEWAY_TIMEOUT_MS=30000

# WP6：预付费钱包
export PILIPALA_BILLING_DEFAULT_JOB_COST=10    # 单次任务冻结 credit 数（正整数）
export PILIPALA_BILLING_INITIAL_CREDITS=0      # 新注册用户初始赠送 credit 数（0 = 不赠送）
export PILIPALA_BILLING_SETTLE_POLL_MS=200     # 后台对账扫描间隔（毫秒）
export PILIPALA_BILLING_SETTLE_BATCH_SIZE=50   # 单轮最多结算多少张冻结单

# WP8：火山引擎 TOS（api 签发预签名读地址，worker 写对象字节）
export PILIPALA_TOS_MODE=stub                  # stub（本地零密钥，写 local-dir）| http（真实火山 TOS）
export PILIPALA_TOS_ENDPOINT=https://tos-cn-beijing.volces.com
export PILIPALA_TOS_REGION=cn-beijing
export PILIPALA_TOS_BUCKET=pilipala-dev
export PILIPALA_TOS_ACCESS_KEY=                # 由环境变量注入，勿入库
export PILIPALA_TOS_SECRET_KEY=                # 由环境变量注入，勿入库
export PILIPALA_TOS_PRESIGN_TTL_SECONDS=900    # 预签名读地址有效期（60..86400）
export PILIPALA_TOS_TIMEOUT_MS=15000
export PILIPALA_TOS_LOCAL_DIR=/tmp/pilipala-tos   # stub 模式的落盘根目录
```

WP10：真实火山引擎 **华南区** 公网 TOS。公网 endpoint 强制 **Virtual-Host 虚拟主机风格**
（`https://<bucket>.tos-cn-guangzhou.volces.com/<object_key>`），路径风格会拿到 `403 InvalidPathAccess`；
`api` 与 `worker` 都已按「回环地址走 Path-Style、真实域名走 Virtual-Host」自动判定。

```bash
export PILIPALA_TOS_MODE=http
export PILIPALA_TOS_REGION=cn-guangzhou
export PILIPALA_TOS_ENDPOINT=https://tos-cn-guangzhou.volces.com
export PILIPALA_TOS_BUCKET=flora-media-test          # 生产桶换成 flora-media-prod
export PILIPALA_TOS_ACCESS_KEY=<your-ak>             # 由环境变量注入，勿入库、勿写进文档
export PILIPALA_TOS_SECRET_KEY=<your-sk>             # 由环境变量注入，勿入库、勿写进文档
```

WP10：真实 Anytoken 网关。`base-url` 结尾的 `/v1` 与 `generate-path` 开头的 `/v1/` 会自动去重，不会拼出 `/v1/v1`。

```bash
export PILIPALA_GATEWAY_MODE=http
export PILIPALA_GATEWAY_BASE_URL=https://api.anytoken.work/v1
export PILIPALA_GATEWAY_GENERATE_PATH=/v1/images/generations
export PILIPALA_GATEWAY_API_KEY=<your-image-key>              # 生图分组 Key
export PILIPALA_GATEWAY_VIDEO_API_KEY=<your-seedance-key>     # 视频分组 Key；留空回退 API_KEY
```

已开通的模型：**生图** `gpt-image-2`、`gpt-image-2.5-flare`、`gpt-image-2.5-sunburst`、`image2`
（前端默认模型是 `gpt-image-2`）；**视频** `seedance2`、`seedance2.5`、`seedance-mini`、`seedance-fast`。

WP12：Seedance 视频是**三步异步协议**（`POST /v1/videos/generations` → 轮询 `GET /v1/videos/{taskId}`
→ 取成片直链），轮询预算由这两个变量控制：

```bash
export PILIPALA_GATEWAY_VIDEO_GENERATE_PATH=/v1/videos/generations   # 提交端点
export PILIPALA_GATEWAY_VIDEO_STATUS_PATH_PREFIX=/v1/videos           # 轮询端点前缀
export PILIPALA_GATEWAY_VIDEO_POLL_INTERVAL_MS=2000                  # 轮询间隔
export PILIPALA_GATEWAY_VIDEO_MAX_POLL_ATTEMPTS=90                   # 轮询上限（默认 90 × 2s ≈ 180s）
```

> **实测提醒**：真实 Seedance 出 5 秒片耗时约 **170–200 秒**，默认预算（180s）**刚好压线**。
> 上游自己的响应里带 `poll_after_ms: 5000`，说明它建议 5 秒轮询一次。
> 若视频比默认预算更慢，任务会以可重试的 `GATEWAY_TIMEOUT` 失败并**重新提交一次上游任务**
> （等于多生成一条视频、多花一份钱）。视频时长更长或上游更慢时，请调大
> `PILIPALA_GATEWAY_VIDEO_MAX_POLL_ATTEMPTS`（例如 `180` = 360s）。

也可以把本地私有文件放在未入库的 `api/config/` 或 `worker/config/` 下再指过去（这两个目录已在 `.gitignore` 里）：

```bash
export SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:./api/config/pilipala-secrets.yml
```

### 3. 构建与启动

仓库根是 Maven 聚合父工程，两个模块是 `api/` 与 `worker/`：

```bash
cd pilipala

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
- `V4__create_wallet_domain.sql` —— 建 `wallet` / `wallet_hold` / `wallet_ledger`，并给 `job.hold_id` 补上物理外键
- `V5__create_i18n_message.sql` —— 建 `i18n_message`，并灌入 stage-1 全部已登记 error_code 与 `JOB_FAILED.RETRY` 的 zh / en 种子（18 行）
- `V6__create_cloud_persistence.sql` —— 建 `canvas_project`（7 列）与 `user_asset`（8 列），并灌入 `PROJECT_NOT_FOUND` / `ASSET_NOT_FOUND`（含 `error.*` 别名）的 zh / en 种子

迁移历史在 `flyway_schema_history` 表里。**迁移统一由 api 管理**，`worker` 不跑 Flyway（`spring.flyway.enabled: false`）。

健康检查（两个进程各一个）：

```bash
curl -s http://127.0.0.1:8080/actuator/health   # {"status":"UP"}（需要 RabbitMQ 在线）
curl -s http://127.0.0.1:8081/actuator/health   # {"status":"UP"}
```

端口可用 `PILIPALA_API_PORT` / `PILIPALA_WORKER_PORT` 覆盖。仓库带 Maven Wrapper，没有 Maven 也能跑；已装 Maven 时把 `./mvnw` 换成 `mvn` 等价。

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
curl -s $API/me -H "pilipala-token: $TOKEN"
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
curl -s -X POST $API/logout -H "pilipala-token: $TOKEN"
curl -s $API/me -H "pilipala-token: $TOKEN"   # → UNAUTHORIZED
```

会话确实落在 Redis 里（键前缀 `pilipala-token:`）：

```bash
redis-cli --scan --pattern 'pilipala-token:*'
# pilipala-token:login:token:<uuid>
# pilipala-token:login:session:<user_id>
```

### 5. curl 验收（WP4 任务接口）

任务接口全部要登录（WP3 的鉴权闸门覆盖整个 `/api/v1/**`）。

```bash
API=http://127.0.0.1:8080/api/v1
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password1"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# submit：写 job(queued) + outbox(pending) + 幂等记录；WP6 起还会冻结余额并返回 hold_id
curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"k1","capability":"image.generate","model":"demo","params":{"prompt":"hi"}}'
# {"ok":true,"data":{"job_id":"...","status":"queued","hold_id":"..."},"error_code":null,"message":null}
# 余额不足时 → {"ok":false,"data":null,"error_code":"WALLET_INSUFFICIENT","message":"账户余额不足，请先充值。"}（不入队）

# 同一个 idempotency_key + 同一个 body → 返回同一个 job_id，不二次入队
curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"k1","capability":"image.generate","model":"demo","params":{"prompt":"hi"}}'

# 同一个 key + 不同的 body → IDEMPOTENCY_CONFLICT
curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"k1","capability":"image.generate","model":"demo","params":{"prompt":"别的"}}'
# {"ok":false,"data":null,"error_code":"IDEMPOTENCY_CONFLICT","message":"同一个幂等键对应了不同的请求内容。"}

# query
curl -s $API/jobs/$JOB_ID -H "pilipala-token: $TOKEN"
# {"ok":true,"data":{"job_id":"...","status":"queued","attempt":0,"max_attempts":3,"error_code":null,"message":null,"updated_at":"..."}}

# result（未成功时 outputs 为空）
curl -s $API/jobs/$JOB_ID/result -H "pilipala-token: $TOKEN"

# cancel：queued 直接变 cancelled，终态幂等
curl -s -X POST $API/jobs/$JOB_ID/cancel -H "pilipala-token: $TOKEN"
# {"ok":true,"data":{"job_id":"...","status":"cancelled","attempt":0,"max_attempts":3,"error_code":"JOB_CANCELLED","message":"任务已取消。", ...}}

# 别人的 job / 不存在的 job → JOB_NOT_FOUND（不泄露存在性）
curl -s $API/jobs/00000000-0000-0000-0000-000000000000 -H "pilipala-token: $TOKEN"
# {"ok":false,"data":null,"error_code":"JOB_NOT_FOUND","message":"任务不存在。"}

# 不带 token → UNAUTHORIZED
curl -s -X POST $API/jobs/submit -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"k2","capability":"image.generate","model":"demo","params":{}}'
# {"ok":false,"data":null,"error_code":"UNAUTHORIZED","message":"请先登录后再试。"}
```

库里能看到 outbox 被投出去（`status` 从 `pending` 变 `sent`）：

```bash
psql -d pilipala -c "SELECT id, aggregate_id, payload, status, sent_at FROM outbox_message ORDER BY created_at DESC LIMIT 5;"
```

消息体只有 `job_id` 与传输层 `event_id`，没有 params、没有图片、没有钱包额（ADR 0003）：

```json
{"job_id": "222ffe85-5f38-4abb-8694-4ea7702abe85", "event_id": "162a51ce-faf9-49f0-ba68-61a7ee3ae179"}
```

RabbitMQ 侧能看到 exchange / queue / 死信队列：

```bash
rabbitmqctl list_queues name messages consumers
# pilipala.jobs.execute        1  0
# pilipala.jobs.execute.dlq    0  0
```

### 6. WP5 双进程端到端（api + worker）

**要同时起 api（8080）与 worker（8081）**，worker 才会把队列里的 job 领走。默认 `PILIPALA_GATEWAY_MODE=stub`：不发外部请求，用本地确定性假上游跑通全链路。

```bash
API=http://127.0.0.1:8080/api/v1
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password1"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 1) 正常任务（stub 默认成功）：submit 后被 worker 消费 → succeeded
JOB_OK=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp5-ok-1","capability":"image.generate","model":"demo","params":{"prompt":"a quiet garden"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 1
curl -s $API/jobs/$JOB_OK -H "pilipala-token: $TOKEN"
# → status="succeeded", attempt=1, error_code=null
curl -s $API/jobs/$JOB_OK/result -H "pilipala-token: $TOKEN"
# → status="succeeded", outputs=[{"type":"image","object_key":"anytoken/img/...","url":null}]

# 2) 上游暂时不可用（stub_outcome=provider_unavailable）：自动重试 3 次后进 dead + 死信队列
JOB_RETRY=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp5-retry-1","capability":"image.generate","model":"demo","params":{"prompt":"fail","stub_outcome":"provider_unavailable"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 3
curl -s $API/jobs/$JOB_RETRY -H "pilipala-token: $TOKEN"
# → status="dead", attempt=3, max_attempts=3, error_code="PROVIDER_UNAVAILABLE", message="生成服务暂时不可用，请稍后重试。"

# 3) 不可重试业务失败（stub_outcome=job_failed）：attempt=1 直接进 dead
JOB_FAIL=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp5-fail-1","capability":"image.generate","model":"demo","params":{"prompt":"bad","stub_outcome":"job_failed"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 1
curl -s $API/jobs/$JOB_FAIL -H "pilipala-token: $TOKEN"
# → status="dead", attempt=1, error_code="JOB_FAILED", message="任务执行失败。"

# 4) 运行中取消：stub_delay_ms 让上游慢一点，取消意图优先于本次 attempt 的结果
JOB_CANCEL=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp5-cancel-1","capability":"image.generate","model":"demo","params":{"prompt":"slow","stub_delay_ms":4000}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 2
curl -s -X POST $API/jobs/$JOB_CANCEL/cancel -H "pilipala-token: $TOKEN"   # 运行中取消：先记意图
sleep 4
curl -s $API/jobs/$JOB_CANCEL -H "pilipala-token: $TOKEN"
# → status="cancelled", error_code="JOB_CANCELLED"（即使上游这次成功也收敛为 cancelled）
```

上游原文只进 `provider_task`，`job_step` 通过 `provider_task_id` 关联：

```bash
psql -d pilipala -c "SELECT job_id, job_step_id, upstream_request_id, raw_status, raw_body FROM provider_task ORDER BY created_at DESC LIMIT 5;"
psql -d pilipala -c "SELECT job_id, attempt, status, error_code, provider_task_id FROM job_step ORDER BY started_at DESC LIMIT 5;"
```

死信队列里能看到 dead 的消息：

```bash
rabbitmqctl list_queues name messages consumers
# pilipala.jobs.execute        0  1
# pilipala.jobs.execute.dlq    2  0
```

Redis 短锁在消费期间是真实存在的（跑完就释放）：

```bash
redis-cli --scan --pattern 'job:lease:*'
# job:lease:<job_id>
```

### 7. WP6 双进程端到端（钱包冻结 / 确认 / 释放）

**同样要同时起 api（8080）与 worker（8081）**：worker 把 job 推进终态后，api 侧的
`WalletHoldSettlementDispatcher`（每 200ms 扫一次）会把冻结单结掉。

先用运维 SQL 充值（**不要**只改 `wallet.available`：必须同时记一笔 `type='topup'` 的流水，
否则余额和流水对不上）：

```bash
psql -d pilipala -c "
WITH w AS (
    INSERT INTO wallet (user_id, available, frozen)
    SELECT id, 100, 0 FROM users WHERE username = 'bob'
    ON CONFLICT (user_id) DO UPDATE
        SET available = wallet.available + 100,
            updated_at = now()
    RETURNING id, user_id
)
INSERT INTO wallet_ledger (wallet_id, user_id, type, amount)
SELECT id, user_id, 'topup', 100 FROM w;
"
```

也可以把 `PILIPALA_BILLING_INITIAL_CREDITS` 设成大于 0 的值，让**新注册**的用户自动带一笔 topup 流水。

```bash
API=http://127.0.0.1:8080/api/v1
TOKEN=$(curl -s -X POST $API/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"password1","locale":"zh"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 1) 新用户余额为 0（initial-credits 默认 0）
curl -s $API/wallet -H "pilipala-token: $TOKEN"
# {"ok":true,"data":{"user_id":"...","available":0,"frozen":0,"updated_at":"..."},"error_code":null,"message":null}

# 2) 余额不足时 submit → WALLET_INSUFFICIENT，且不入队（job / wallet_hold / wallet_ledger / outbox / 幂等记录都是 0 行）
curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp6-ok-1","capability":"image.generate","model":"demo","params":{"prompt":"sunflower"}}'
# {"ok":false,"data":null,"error_code":"WALLET_INSUFFICIENT","message":"账户余额不足，请先充值。"}

# 3) 充值 100（上面的 psql），然后用**同一个** idempotency_key 重新提交
psql -d pilipala -c "..."   # 见本节开头的充值 SQL
JOB_OK=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp6-ok-1","capability":"image.generate","model":"demo","params":{"prompt":"sunflower"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
# 提交瞬间：available=90 frozen=10，hold_id 非空
curl -s $API/wallet -H "pilipala-token: $TOKEN"

# 4) 成功 → 自动 confirm（冻结转实扣）
sleep 2
curl -s $API/jobs/$JOB_OK -H "pilipala-token: $TOKEN"     # status="succeeded"
curl -s $API/wallet -H "pilipala-token: $TOKEN"            # available=90 frozen=0

# 5) 同 key 同 body 重放：同一个 job_id、同一个 hold_id，不二次冻结
curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp6-ok-1","capability":"image.generate","model":"demo","params":{"prompt":"sunflower"}}'

# 6) 不可重试失败（stub_outcome=job_failed）：dead → 自动 release（失败不吞冻）
JOB_FAIL=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp6-fail-1","capability":"image.generate","model":"demo","params":{"prompt":"bad","stub_outcome":"job_failed"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 2
curl -s $API/jobs/$JOB_FAIL -H "pilipala-token: $TOKEN"   # status="dead", error_code="JOB_FAILED"
curl -s $API/wallet -H "pilipala-token: $TOKEN"            # available 回到扣费前

# 7) 可重试失败（stub_outcome=provider_unavailable）：重试期间 hold 保持 frozen，不重冻也不释放
JOB_RETRY=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp6-retry-1","capability":"image.generate","model":"demo","params":{"prompt":"retry","stub_outcome":"provider_unavailable"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 1
curl -s $API/wallet -H "pilipala-token: $TOKEN"            # available=80 frozen=10（还冻着）
sleep 4
curl -s $API/jobs/$JOB_RETRY -H "pilipala-token: $TOKEN"   # status="dead", attempt=3
curl -s $API/wallet -H "pilipala-token: $TOKEN"            # available 回到扣费前，frozen=0

# 8) 运行中取消（stub_delay_ms=3000）：cancelled → 自动 release
JOB_CANCEL=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp6-cancel-1","capability":"image.generate","model":"demo","params":{"prompt":"slow","stub_delay_ms":3000}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
curl -s -X POST $API/jobs/$JOB_CANCEL/cancel -H "pilipala-token: $TOKEN"
sleep 4
curl -s $API/jobs/$JOB_CANCEL -H "pilipala-token: $TOKEN"  # status="cancelled", error_code="JOB_CANCELLED"
curl -s $API/wallet -H "pilipala-token: $TOKEN"             # available 回到扣费前，frozen=0
```

对账（三张表 + 流水不变量）：

```bash
psql -d pilipala -c "SELECT available, frozen FROM wallet WHERE user_id = (SELECT id FROM users WHERE username = 'bob');"
psql -d pilipala -c "SELECT id, job_id, amount, status, finalized_at FROM wallet_hold WHERE user_id = (SELECT id FROM users WHERE username = 'bob') ORDER BY created_at;"
psql -d pilipala -c "SELECT type, amount, hold_id, job_id, created_at FROM wallet_ledger WHERE user_id = (SELECT id FROM users WHERE username = 'bob') ORDER BY created_at;"

# available == Σ(topup) - Σ(freeze) + Σ(release)
# frozen    == Σ(freeze) - Σ(confirm) - Σ(release) == Σ(status='frozen' 的 hold.amount)
psql -d pilipala -c "
SELECT w.available,
       COALESCE(SUM(l.amount) FILTER (WHERE l.type='topup'),0)
     - COALESCE(SUM(l.amount) FILTER (WHERE l.type='freeze'),0)
     + COALESCE(SUM(l.amount) FILTER (WHERE l.type='release'),0) AS available_by_ledger,
       w.frozen,
       COALESCE(SUM(l.amount) FILTER (WHERE l.type='freeze'),0)
     - COALESCE(SUM(l.amount) FILTER (WHERE l.type='confirm'),0)
     - COALESCE(SUM(l.amount) FILTER (WHERE l.type='release'),0) AS frozen_by_ledger,
       (SELECT COALESCE(SUM(h.amount),0) FROM wallet_hold h WHERE h.user_id = w.user_id AND h.status='frozen') AS frozen_by_holds
FROM wallet w JOIN wallet_ledger l ON l.wallet_id = w.id
WHERE w.user_id = (SELECT id FROM users WHERE username = 'bob')
GROUP BY w.available, w.frozen, w.user_id;"
```

### 9. WP8 双进程端到端（火山 TOS 结果落地与签名读 URL）

默认 `PILIPALA_TOS_MODE=stub`：**零云密钥**。worker 把生成结果字节写到本地
`${PILIPALA_TOS_LOCAL_DIR}/{bucket}/{object_key}`，api 照样按 TOS V4 规则签出短期预签名 GET 地址。

```bash
API=http://127.0.0.1:8080/api/v1
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"password1"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 1) 提交一个会成功的任务（余额要先充够，见第 7 节）
JOB=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp8-ok-1","capability":"image.generate","model":"demo","params":{"prompt":"a quiet garden"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 2

# 2) result：object_key 是 TOS 对象键，url 是短期签名读地址
curl -s $API/jobs/$JOB/result -H "pilipala-token: $TOKEN"
# {"ok":true,"data":{"job_id":"...","status":"succeeded","outputs":[
#   {"type":"image","object_key":"anytoken/img/<job_id>/1.png",
#    "url":"http://127.0.0.1:18080/pilipala-dev/anytoken/img/<job_id>/1.png?X-Tos-Algorithm=TOS4-HMAC-SHA256&X-Tos-Credential=...&X-Tos-Date=...&X-Tos-Expires=900&X-Tos-SignedHeaders=host&X-Tos-Signature=..."}],"error_code":null,"message":null}}

# 3) 落盘核验：文件真的写出来了，且是合法 PNG（文件头 89 50 4e 47）
ls -l /tmp/pilipala-tos/pilipala-dev/anytoken/img/$JOB/1.png
xxd -l 8 /tmp/pilipala-tos/pilipala-dev/anytoken/img/$JOB/1.png
# 89504e47 0d0a1a0a

# 4) 取回字节：把签名 URL 指到本地文件服务上就能真的 GET 回来
python3 -m http.server 18080 --directory /tmp/pilipala-tos &
# 用 PILIPALA_TOS_ENDPOINT=http://127.0.0.1:18080 重启 api 后，签名 URL 直接可访问：
# curl -s "$URL" -o /tmp/fetched.png && xxd -l 8 /tmp/fetched.png

# 5) TOS 故障：不吞冻、不假成功 → 重试 3 次后 dead，钱包冻结额自动退回
JOB_TOS_FAIL=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp8-tos-fail-1","capability":"image.generate","model":"demo","params":{"prompt":"bad","stub_tos_outcome":"unavailable"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')
sleep 4
curl -s $API/jobs/$JOB_TOS_FAIL -H "pilipala-token: $TOKEN"
# → status="dead", attempt=3, error_code="PROVIDER_UNAVAILABLE"
curl -s $API/wallet -H "pilipala-token: $TOKEN"
# → available 回到扣费前，frozen=0（失败不吞冻）
```

接真实火山 TOS：配齐桶与 AK/SK，设 `PILIPALA_TOS_MODE=http`、
`PILIPALA_TOS_ENDPOINT=https://tos-cn-beijing.volces.com`（或对应 region 的端点），
并让 `PILIPALA_TOS_BUCKET` 与桶名一致即可，worker 走 V4 Header 签名 PUT，api 走 V4 Query 预签名 GET。

### 10. WP9 友好错误与 `i18n_message`（改词条即时生效）

WP9 起 C 端的 `message` 不再写死在代码里，而是按 locale 查 `i18n_message` 表。
**运维改表即时生效，不用重启 api**（stage-1 只有几十行词条，没有缓存）。

语言选择优先级：登录用户的 `users.locale` 偏好 > 请求 `Accept-Language` > `zh`。

```bash
API=http://127.0.0.1:8080/api/v1
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"password1"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 1) 缺词条回退：查一个不存在的 job → JOB_NOT_FOUND
curl -s $API/jobs/00000000-0000-0000-0000-000000000000 -H "pilipala-token: $TOKEN"
# {"ok":false,"data":null,"error_code":"JOB_NOT_FOUND","message":"任务不存在。"}

# 2) Accept-Language 决定语言
curl -s $API/jobs/00000000-0000-0000-0000-000000000000 -H "pilipala-token: $TOKEN" -H 'Accept-Language: en-US'
# {"ok":false,"data":null,"error_code":"JOB_NOT_FOUND","message":"The task does not exist."}

# 3) 用户偏好优先于请求头：users.locale='en' 后，不带 Accept-Language 也是英文
psql -d pilipala -c "UPDATE users SET locale='en' WHERE username='bob';"
curl -s $API/jobs/00000000-0000-0000-0000-000000000000 -H "pilipala-token: $TOKEN"
# → message="The task does not exist."
psql -d pilipala -c "UPDATE users SET locale=NULL WHERE username='bob';"   # 还原

# 4) 改词条即时生效：改完直接再 curl，**不要重启 api**
psql -d pilipala -c "UPDATE i18n_message SET template='账户余额不足，请先充值或换一台机器。' WHERE key='WALLET_INSUFFICIENT' AND locale='zh';"
curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp9-1","capability":"image.generate","model":"demo","params":{}}'
# {"ok":false,"data":null,"error_code":"WALLET_INSUFFICIENT","message":"账户余额不足，请先充值或换一台机器。"}
psql -d pilipala -c "UPDATE i18n_message SET template='账户余额不足，请先充值。' WHERE key='WALLET_INSUFFICIENT' AND locale='zh';"   # 还原

# 5) 缺词条回退链：DB(en) → DB(zh) → 内置种子
psql -d pilipala -c "DELETE FROM i18n_message WHERE key='JOB_NOT_FOUND' AND locale='en';"
curl -s $API/jobs/00000000-0000-0000-0000-000000000000 -H "pilipala-token: $TOKEN" -H 'Accept-Language: en-US'
# → message="任务不存在。"（DB 里 en 那条没了，回退到 zh 那条）
psql -d pilipala -c "DELETE FROM i18n_message WHERE key='JOB_NOT_FOUND' AND locale='zh';"
curl -s $API/jobs/00000000-0000-0000-0000-000000000000 -H "pilipala-token: $TOKEN" -H 'Accept-Language: en-US'
# → message="The task does not exist."（表里两条都没了，回退到内置种子）

# 还原种子
psql -d pilipala -c "
INSERT INTO i18n_message (key, locale, template) VALUES
  ('JOB_NOT_FOUND', 'zh', '任务不存在。'),
  ('JOB_NOT_FOUND', 'en', 'The task does not exist.')
ON CONFLICT (key, locale) DO NOTHING;"

# 6) 词条现状
psql -d pilipala -c "SELECT key, locale, template FROM i18n_message ORDER BY key, locale;"
```

### 11. WP7 前端联调（登录 + 钱包 + 生图闭环）

WP7 起前端默认走 **托管云端模式**：浏览器只跟 pilipala 的 `api` 说话，不碰模型供应商，
也不在浏览器里放任何供应商 API Key（ADR 0001）。生图按 ADR 0003 走
`submit → 轮询 query → result`，取消调 `POST /api/v1/jobs/{jobId}/cancel`。

#### 11.1 一起要起的东西

| 进程 | 端口 | 备注 |
| --- | --- | --- |
| `api` | 8080 | 见第 3 节 |
| `worker` | 8081 | 见第 3 节，不起 worker job 不会被消费 |
| 本地 TOS 文件桶 | 18080 | stub 模式的落盘目录 `/tmp/pilipala-tos` |
| Vite dev server | 3000 | `web/` 下 `bun run dev` |

本地桶要带 CORS 头，否则浏览器 `fetch` 签名地址会被拦（前端会退化成直链渲染，
但拿不到 base64 字节）：

```bash
python3 -c "
import http.server, os
os.chdir('/tmp/pilipala-tos')
class H(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        super().end_headers()
http.server.HTTPServer(('127.0.0.1', 18080), H).serve_forever()
"
```

`api` 的 TOS 端点指到它：`PILIPALA_TOS_MODE=stub`、`PILIPALA_TOS_ENDPOINT=http://127.0.0.1:18080`、
`PILIPALA_TOS_BUCKET=pilipala-dev`、`PILIPALA_TOS_LOCAL_DIR=/tmp/pilipala-tos`（默认值即可）。

前端：

```bash
cd pilipala/web
bun install
bun run dev        # http://localhost:3000
```

Vite dev server 会把 `/api` 代理到 `http://localhost:8080`（`VITE_API_PROXY_TARGET` 可改），
所以浏览器里不用配任何地址。

#### 11.2 走一遍

1. 打开 <http://localhost:3000>，右上角点「登录 / 注册」→ 注册一个账号
   （用户名 `^[a-zA-Z0-9_]{3,32}$`，密码 ≥ 8 位）。成功后顶栏出现 **钱包余额** 与用户名。
   令牌存 localStorage 的 `flora:auth_token`，请求头名是 `pilipala-token`。
2. 余额不够时生图会被拦：报错是后端返回的本地化短句「账户余额不足，请先充值。」，
   `error_code` 是 `WALLET_INSUFFICIENT`，**不入队也不冻结**。本地加余额只能用第 7 节的运维 SQL。
3. 余额够时点「开始生成」：前端 `POST /api/v1/jobs/submit` → 每 800ms 轮询
   `GET /api/v1/jobs/{jobId}` → 成功后 `GET /api/v1/jobs/{jobId}/result` 取 `outputs[].url`
   （api 现签的 TOS 预签名地址）→ 浏览器取字节渲染。
4. 钱对得上：提交瞬间 `available` 减、`frozen` 加；成功后冻结转实扣（`frozen` 回 0）；
   失败或取消则整笔退回。
5. 取消：生成中点停止 → `POST /api/v1/jobs/{jobId}/cancel` → 任务收敛成 `cancelled`
   （`error_code=JOB_CANCELLED`），冻结额退回。
6. 没登录就点生成：后端返 `UNAUTHORIZED`，前端弹出登录框。

本地实测（api + worker + 本地桶 + Vite，浏览器真实跑过两轮）：

```
未登录 → error_code=UNAUTHORIZED message="请先登录后再试。"（并弹出登录框）
登录 → 顶栏显示余额与用户名；余额 180
生图 → 847ms 返回 1 张，mimeType=image/png，bytes=67，dataUrl 为 base64
余额 180 → 170（单次扣 10 credit，PILIPALA_BILLING_DEFAULT_JOB_COST）
取消 → submit 得到 queued/hold_id → 取消前 query=running → cancel → 之后 query=cancelled（JOB_CANCELLED）→ 余额回到 170，frozen=0
余额不足 → error_code=WALLET_INSUFFICIENT message="账户余额不足，请先充值。"，余额与冻结额都没变
```

#### 11.3 等价的 curl 验收

前端把令牌放在 localStorage（`flora:auth_token`），请求头是 `pilipala-token`。
用 curl 走同一条链路：

```bash
API=http://127.0.0.1:8080/api/v1
TOKEN=$(curl -s -X POST $API/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"wp7demo","password":"password1","locale":"zh"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 钱包余额（顶栏显示的就是它）
curl -s $API/wallet -H "pilipala-token: $TOKEN"

# submit（params 只放已物化参数：不要塞整张画布或参考图 base64）
JOB=$(curl -s -X POST $API/jobs/submit -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"idempotency_key":"wp7-1","capability":"image.generate","model":"gpt-image-1","params":{"prompt":"a quiet garden","size":"auto","quality":"auto"}}' \
  | sed -n 's/.*"job_id":"\([^"]*\)".*/\1/p')

# 轮询 query → result
curl -s $API/jobs/$JOB -H "pilipala-token: $TOKEN"
curl -s $API/jobs/$JOB/result -H "pilipala-token: $TOKEN"

# 取消
curl -s -X POST $API/jobs/$JOB/cancel -H "pilipala-token: $TOKEN"
```

### 12. WP10 跨会话 / 换浏览器云端持久化验证

WP10 起，**画布项目、云端素材、生图任务历史**随账号存在服务端（浏览器本地只留图片字节）。
验证顺序：建项目 → 存画布节点 → 生图成功 → 换浏览器登录同一账号 → 三个接口都能读到。

```bash
API=http://127.0.0.1:8080/api/v1
TOKEN=$(curl -s -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password1"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 1) 建云端画布项目
PROJECT_ID=$(curl -s -X POST $API/projects -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"wp10 画布","canvas_data":{"nodes":[],"connections":[]}}' \
  | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')

# 2) 存画布快照（每次成功更新 version +1）
curl -s -X PUT $API/projects/$PROJECT_ID -H "pilipala-token: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"wp10 画布","canvas_data":{"nodes":[{"id":"n1","type":"text","title":"hi","position":{"x":0,"y":0},"width":200,"height":100}],"connections":[]}}'
# → data.version = 2

# 3) 项目摘要列表：**不含** canvas_data
curl -s $API/projects -H "pilipala-token: $TOKEN"

# 4) 项目详情：含 canvas_data
curl -s $API/projects/$PROJECT_ID -H "pilipala-token: $TOKEN"

# 5) 云端素材：自动同步已 succeeded 任务的输出，并现签读地址
curl -s "$API/assets?limit=100" -H "pilipala-token: $TOKEN"
# → data[].object_key 是对象键，data[].signed_url 是现签地址（库里不落预签名 URL）

# 6) 生图历史（含 prompt、cost_credits、result_urls 与本地化错误）
curl -s "$API/jobs/history?limit=50" -H "pilipala-token: $TOKEN"

# 7) 越权：另一个用户访问上面的 project_id → PROJECT_NOT_FOUND（不泄露存在性）
curl -s $API/projects/$PROJECT_ID -H "pilipala-token: $OTHER_TOKEN"
# → {"ok":false,"data":null,"error_code":"PROJECT_NOT_FOUND","message":"..."}
```

浏览器端：

1. 登录后「画布」页显示「已开启账号云同步」；新建画布后地址里的 id 是 **UUID**（云端 id），不是本地 nanoid。
2. 画布里加节点，约 1 秒后顶栏出现「已同步 v{version}」；点它可立即保存。
3. 「我的资产」页点「同步云端素材」，能看到刚生成成功的图片（来源标记「云端生成」）。
4. 「生图」页的生成历史里能看到云端任务记录（成功任务的签名图片、失败任务的友好错误）。
5. **换浏览器或清空 localStorage 后登录同一账号** → `/canvas`、`/assets`、`/image` 立刻能看到上面这些内容。
6. 未登录时显示「本地未同步」，内容只在本浏览器，界面不谎称已上云。

### 13. WP5 / WP6 / WP7 / WP8 / WP9 / WP10 的边界（不要误以为已经能用）

- **C 端充值（WP16）**：`PILIPALA_PUBLIC_BASE_URL` 由 env 注入（**代码不写死域名**）。**生产**已确认为 `https://pilipala.art`（无尾 `/`），对应 notify
  `https://pilipala.art/api/v1/billing/recharge/webhook/zpay`、return `https://pilipala.art/billing/recharge/result`。
  **本地**可设为 `http://127.0.0.1:5173`（或你的前端 origin）以便 z-pay 回调打到隧道；`PILIPALA_ZPAY_PID` / `PILIPALA_ZPAY_PKEY` 仅占位，值放本机 env / vault，**不要**入库。创建订单：
  `curl -H "pilipala-token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"package_id":"p10"}' http://127.0.0.1:8080/api/v1/billing/recharge/orders`。
  未配置时创建订单应返回 `VALIDATION_ERROR`。webhook 路径 `/api/v1/billing/recharge/webhook/zpay` 无需登录。
- **仍可用运维加额**：SQL、`PILIPALA_BILLING_INITIAL_CREDITS`、Admin `POST /api/v1/admin/wallets/{userId}/topup`。
- **TOS 默认是 stub**：WP8 默认 `PILIPALA_TOS_MODE=stub`，结果字节写本地 `/tmp/pilipala-tos`，
  不是真的对象存储；配齐真实火山 TOS 桶与 AK/SK 并设 `PILIPALA_TOS_MODE=http` 才直连线上 TOS。
- **签名 URL 是短期的**：默认 TTL 900 秒，过期后前端要重新调 `result` 拿新的地址。
- **钱包不等于钱**：pilipala credit 是产品内部的计费单位；人民币支付经 WP16 `recharge_order` 换算为 credit 入账。
  全额退款走 Admin `POST /api/v1/admin/billing/recharge-orders/{id}/refund`（WP16）。
- **默认不连真上游**：`PILIPALA_GATEWAY_MODE=stub` 是本地假上游；`http` 模式才真的调 Anytoken，
  而且本 WP 没有接真实付费账号与账单。
- **生成结果是假的**：WP5 还没接 TOS，`result[].url` 恒为 null，只有合成的 `object_key`。
  **不要把「submit 能跑通」写成「托管 pilipala 已可用」**。
- **没有 DLQ 重投运营台**：死信只进队列，没有 UI（WP11）。
- **没有超时孤儿回收**：running 状态卡住的任务不会被定时扫回 queued（属运维策略，不在本 WP）。
- **没有运营改词条入口**：WP9 只把词条落进 `i18n_message` 表，改词条要走运维 SQL；
  Admin CRUD 留给 WP11。
- **C 端静态 UI 文案不在库里**：`i18n_message` 只放服务端错误模板与运营可改的动态提示；
  按钮、导航栏、设置项文案仍在 `web/src/i18n/locales/`（两层 i18n 不混用）。
- **前端托管模式只覆盖生图**：WP7 只把 **图片生成** 接到 pilipala API；
  视频、音频、文本仍然走原来的本地直连渠道。
- **画布云端持久化只覆盖画布项目本身**：WP10 起画布项目、云端素材与生图历史随账号存服务端，
  但**图片字节仍留在浏览器本地**（库里只存 TOS 对象键），所以「换个设备」看到的是云端项目结构与
  云端素材，节点里的本地图片仍以本地存储为准。没有做多人实时协同（属非目标）。
- **云端素材删除会被自动同步覆盖**：`GET /api/v1/assets` 会把已 `succeeded` 任务的输出幂等同步回
  `user_asset`，所以删掉一条「生成」素材后，下次列表它会重新出现（V6 的 `user_asset` 没有删除墓碑列）。
- **前端托管模式没有充值入口**：WP7 只读余额，余额只能靠运维 SQL 补。
- **签名 URL 过期要重新取**：默认 900 秒；过期后前端重新调 `result` / `assets` 拿新地址。

## TBD：pilipala 业务进程（WP5 之后）

下列内容在对应 WP 落地前 **不要当作可执行步骤**：

- 火山 TOS 的 STS 临时凭证与回调（WP8 只做了存储与签名读地址）
- Anytoken 的真实付费调用方式（WP5 只有适配器边界 + 本地 stub）
- 支付渠道与充值订单（钱包在 WP6，但「钱怎么进来」要到 M4 / WP11 Admin）
- 多人实时协同画布与前端直传 TOS（WP10 只做画布 JSON 快照上云 + 生成结果资产与历史持久化）

## TBD：一键编排

M2 之前如果要补 `docker-compose.pilipala.yml`，必须在 brief 里列出服务：`web`、`api`、`worker`、`postgres`、`redis`、`rabbitmq`。TOS 与 Anytoken 通常是云服务，本地用配置指向测试实例，不要在 compose 里写真实 Key。

## 诚实对照

| 问题 | 现在 | 目标 |
| --- | --- | --- |
| pilipala 后端进程能起吗？ | WP1：`api` / `worker` 骨架 + `/actuator/health` | WP4+ 接任务链路 |
| 如何登录 pilipala 账号？ | WP3：`/api/v1/auth/register`、`/login`、`/logout`、`/me`，会话存 Redis；**WP7 前端已有登录/注册弹窗，顶栏显示登录态** | WP7 前端登录页 |
| 如何提交托管任务？ | WP5：submit → worker 消费 → succeeded / dead 闭环跑通；WP6 起 submit 会先冻结余额，**但仍没有真上游**；WP7 前端生图按钮已接上这条链路 | WP4–WP7 |
| 如何看 RabbitMQ 死信？ | WP5：dead 的消息会进 `pilipala.jobs.execute.dlq`，**但没有重投运营台** | WP4 + 以后的 runbook |
| 如何看余额 / 扣费？ | WP6：`GET /api/v1/wallet` 查余额；submit 先冻结，成功 confirm、失败 / 取消 release；**WP7 顶栏显示余额，生成后自动刷新** | WP7 前端余额展示 |
| 如何加测试余额？ | WP6：运维 psql 充值 SQL（必须同时写 topup 流水）或 `PILIPALA_BILLING_INITIAL_CREDITS`；**没有 C 端自助充值接口** | M4 / WP11 admin |
| 生成结果存在哪、怎么读？ | WP8：结果字节进火山 TOS，`result[].object_key` 是对象键，`result[].url` 是 api 现签的短期预签名地址；**默认是 stub 模式写本地 `/tmp/pilipala-tos`**；WP7 前端直接用这个 url 取字节渲染 | WP7 前端直接用 url 展示图片 |
| 错误文案在哪改？ | WP9：C 端 `message` 按 locale 查 `i18n_message` 表，运维 psql 改词条即时生效；**没有 Admin 界面**（WP11） | WP11 运营后台 |
| 前端生图走哪条路？ | WP7：默认 **托管云端模式**，生图走 pilipala `api`（不直连供应商、不在浏览器存供应商 Key）；「配置 → 渠道」可切回本地直连 | 全能力托管 |
| 画布 / 素材 / 生成历史存哪？ | WP10：已登录时画布项目、云端素材、生图历史走 `api` 的 `/api/v1/projects`、`/api/v1/assets`、`/api/v1/jobs/history`，换浏览器登录同一账号可见；**未登录时只存本地并标「本地未同步」**；图片字节仍留浏览器本地，库里只存 TOS 对象键 | WP10 云端持久化 |

如果你发现某份对外 README 把上述目标写成「已经支持」，开 PR 改掉它，不要在本 runbook 里用假命令圆过去。
