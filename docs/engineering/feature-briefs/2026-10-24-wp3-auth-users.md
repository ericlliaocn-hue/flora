# Feature brief：WP3 认证与用户

- 日期：2026-10-24
- 作者：flora engineering
- 对应 WP / 里程碑：WP3 / M1
- 状态：已确认（实现中）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax（上方面包屑硬约束一并保留）。不要摘要。

本任务落地 **第一段业务实现**：`users` 表 + Sa-Token 注册/登录/登出/会话 + C 端 `/api/v1/**` 鉴权闸门。

## 背景

WP0 白标、WP1 api/worker 空骨架、WP2 字段级 schema 已合入 main。api 已引入 `sa-token-spring-boot3-starter`（父 POM `sa-token.version=1.46.0`）与空配置，但 **无登录、无 Postgres、无 Redis、无 Flyway、无用户表**。

[DEVELOPMENT.md](../DEVELOPMENT.md) WP3：

> Sa-Token 登录/登出/会话；用户主数据；C 端请求必须带会话。users 表最少列见 [data-model/README.md](../data-model/README.md)。
>
> 不做：社交登录全家桶、权限中台、admin 角色矩阵（内部角色可预留，UI 放到 WP11）。
>
> 完成：未登录不能 submit；登录后能拿到稳定 user_id。

说明：`submit` 业务在 WP4 才建。本 WP 必须把鉴权拦截路径覆盖到 `/api/v1/jobs/**`（及整个 `/api/v1/**` 白名单外路径），使 WP4 落地 submit 时 **继承同一闸门**，并用 `/api/v1/auth/me` + 拦截配置证明「无会话 → UNAUTHORIZED」。

## 必须遵守

- [ADR 0002](../adr/0002-java-spring-business-api.md)：业务 API = Java 21 + Spring Boot 3；认证 = Sa-Token
- [ADR 0005](../adr/0005-friendly-errors-vs-raw-upstream.md)：C 端 `error_code` + 友好 `message`；禁止异常原文出站
- [TECH_STACK.md](../TECH_STACK.md)：Postgres 权威用户表；Redis 会话辅助；不换栈
- [SECURITY.md](../SECURITY.md)：Sa-Token / Redis / DB 密码只走环境变量；禁止默认 `123456` 进可提交配置当生产值；密钥不入库
- [data-model/README.md](../data-model/README.md)：`users` 列级预留（本 WP 照抄，禁止加社交/角色列）
- [data-model/errors.md](../data-model/errors.md)：本 WP 只用已登记的 `UNAUTHORIZED`、`VALIDATION_ERROR`（不要发明新码）
- [CONTRIBUTING.md](../CONTRIBUTING.md)：只做 brief 范围；不改 `docs/content/**`；不顺手做 WP4–WP11

## 本 WP 拍板（已决定，禁止再猜）

| 项 | 锁定值 |
| --- | --- |
| 登录方式 | 仅 username + password（无 OAuth / 短信 / 邮箱魔法链） |
| 密码哈希 | BCrypt（spring-security-crypto 的 `BCryptPasswordEncoder`，strength 10）。只引 crypto，不要拉起整套 Spring Security FilterChain 抢 Sa-Token |
| 会话 | Sa-Token；`loginId` = `users.id` 的 UUID 字符串；token 名沿用 `flora-token` |
| token 传递 | 响应体返回 token；客户端之后用请求头 `flora-token: <token>`。`is-read-header=true`；本 WP 关闭 cookie 读（`is-read-cookie=false`） |
| 会话存储 | Redis（`sa-token-redis-jackson` + `spring-boot-starter-data-redis`）。禁止只用进程内存冒充「已完成会话」 |
| 用户表 | 仅 WP2 预留列：`id, username, password_hash, locale, created_at, updated_at` |
| 迁移 | Flyway；首个脚本只建 users（见下方 SQL 语义） |
| API 前缀 | `/api/v1/auth/*` |
| 公共信封 | 与 gateway-contract 一致：`{ "ok", "data", "error_code", "message" }`，字段 snake_case |
| 鉴权范围 | 拦截 `/api/v1/**`；放行 `/api/v1/auth/register`、`/api/v1/auth/login`、`/actuator/**` |
| 错误文案（临时） | 仅 `UNAUTHORIZED` / `VALIDATION_ERROR` 的 zh+en 静态 Map（或等价小类）；WP9 再迁 `i18n_message`。禁止 `e.getMessage()` |

## 本 WP 不拍板（禁止发明，留给后续 brief）

- OAuth / 社交登录提供商、短信、邮箱验证、找回密码、MFA
- Cookie 会话、跨域 Cookie、前端登录页 / 画布改打 API（WP7）
- `users` 上加 `email` / `phone` / `role` / `status` 等列（要加必须先改 data-model + 新 brief）
- Admin 角色矩阵、权限中台、RBAC UI（WP11）
- 完整 `docker-compose.flora.yml`（web+api+worker+全家桶）；本 WP 最多给 Postgres+Redis 的可选依赖 compose，且不得写真实密码
- Job / outbox / RabbitMQ / 钱包 / Anytoken / TOS
- 新的 `error_code`（需要时先改 errors.md）

## 目标

1. Postgres 存在 `users` 表，列与 [data-model/README.md](../data-model/README.md) 一致；由 Flyway 创建。
2. 可 register → 行写入且 `password_hash` 为 BCrypt；可 login → Sa-Token 会话落 Redis，返回稳定 `user_id` + token。
3. 可 logout → 会话失效；之后带旧 token 访问受保护接口 → UNAUTHORIZED。
4. `GET /api/v1/auth/me`（需登录）返回当前 `user_id` / `username` / `locale`。
5. 未带有效会话访问 `/api/v1/**`（白名单外，含未来的 `/api/v1/jobs/**`）→ HTTP 业务失败信封，`error_code=UNAUTHORIZED`。
6. [docs/engineering/runbooks/local-dev.md](../runbooks/local-dev.md) 写清：本阶段 **需要 JDK21 + Postgres + Redis**；启动、迁移、curl 验收命令可复制执行。
7. 本 brief 落盘，并更新 [feature-briefs/README.md](./README.md) 索引行。

## 范围

允许新增 / 修改：

| 路径 | 动作 |
| --- | --- |
| `docs/engineering/feature-briefs/2026-10-24-wp3-auth-users.md` | 新增（本文件全文落入仓） |
| `docs/engineering/feature-briefs/README.md` | 索引表加 WP3 一行 |
| `api/pom.xml` | 增加：JDBC、PostgreSQL 驱动、Flyway、Redis、`sa-token-redis-jackson`、`spring-security-crypto`（仅 crypto）、测试依赖（任选 Testcontainers 或文档化手工验收） |
| `pom.xml`（父） | 仅当需要统一管理上述依赖版本时；不要升级 Spring Boot / 不要换 Sa-Token 大版本，除非 brief 外已确认（本任务默认 **不升**） |
| `api/src/main/resources/application.yml` | 补 Postgres / Redis / Flyway / Sa-Token 完备配置；密钥与密码全部 `${ENV:}`；修正注释「WP2 登录」→「WP3」 |
| `api/src/main/resources/application-local.yml` | 本地非密钥默认（本地 JDBC/Redis 地址端口）；禁止提交真实密码 |
| `api/src/main/resources/db/migration/V1__create_users.sql` | 仅 users 表 |
| `api/src/main/java/com/flora/api/auth/**`（建议分包） | Interface 控制器、Application 用例、Domain 用户、Infrastructure 仓储 / Sa-Token 配置 / 统一异常与错误信封 |
| `api/src/main/java/com/flora/api/ApiApplication.java` | 更新过时注释（WP 编号） |
| `api/src/main/java/com/flora/api/{job,billing,gateway}/package-info.java` | 仅修正 WP 编号注释（`job` 应为 WP4 起，勿在本 WP 写 job 实现） |
| `docs/engineering/runbooks/local-dev.md` | WP3 启动与 curl 验收；诚实标注仍无 Job/钱包 |
| `docs/engineering/data-model/README.md` | `users`「首次落地」保持 WP3；可将「仅字段预留」改为「WP3 已落地」并链到本 brief（不要改列定义） |
| 可选 `docker-compose.deps.yml`（或 `deploy/docker-compose.deps.yml`） | 仅 Postgres + Redis；示例密码用占位；写入 runbook。不做 web/api/worker 一键编排 |
| `.gitignore` | 若需忽略本地 secrets，沿用 / 小补，勿扩大无关 |

### 建议 Java 包（可微调命名，但职责必须拆开，禁止上帝类）

```
com.flora.api.auth
  web/          AuthController, 全局异常 → 信封
  application/  RegisterUser, LoginUser, LogoutUser, GetCurrentUser
  domain/       User（或 record）
  infra/        UserEntity, UserRepository, SaTokenConfig, PasswordHasher, AuthErrorMessages
```

不要把认证塞进 job / billing / gateway 包。

## 非范围

- 任何 Job / job_step / outbox / idempotency / RabbitMQ 消费者或生产者
- 钱包 freeze/confirm/release、ledger
- Anytoken HTTP 客户端、TOS、STS、回调
- 前端 `web/` 登录 UI、改打 flora API、locale 大改
- Admin、RBAC、角色列、第二套用户表
- NewAPI / 双账本 / 胖 worker
- `docs/content/**`、公开 Fumadocs、CHANGELOG 用户叙事
- 在 worker 模块加 Sa-Token 或用户 API（worker 保持瘦，不面向 C 端登录）
- 新建 `error_code` 或提前建 `i18n_message` 表（WP9）
- 社交登录、邮箱唯一、密码重置、验证码、滑动窗口限流「顺便做」
- 把 Spring Security 整链拉起来替代 Sa-Token

## 数据表字段

照抄 [data-model/README.md](../data-model/README.md)，禁止改名改语义加列：

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK；默认 `gen_random_uuid()` | Sa-Token loginId |
| `username` | TEXT | NO | UNIQUE | 登录名 |
| `password_hash` | TEXT | NO | | BCrypt 哈希 |
| `locale` | TEXT | YES | 建议 CHECK ∈ zh\|en 或应用层校验 | 可空；空则回退 Accept-Language / zh |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

### Flyway `V1__create_users.sql` 语义要求

- 启用 / 依赖 pgcrypto 或用 Postgres 内置 `gen_random_uuid()`（PG13+ 一般可用；若需扩展，迁移里 `CREATE EXTENSION IF NOT EXISTS pgcrypto`）
- `username` UNIQUE 索引
- 不要建 job / wallet / 其它表

## API

公共信封（成功 / 失败）：

```json
{ "ok": true, "data": {}, "error_code": null, "message": null }
```

失败时 `ok=false`，`data` 可为 null，`error_code` + `message` 必填。

### `POST /api/v1/auth/register`

请求：

| 字段 | 类型 | 必填 | 规则 |
| --- | --- | --- | --- |
| `username` | string | 是 | 长度 3–32；`[a-zA-Z0-9_]+` |
| `password` | string | 是 | 长度 ≥ 8；响应不得回显 |
| `locale` | string | 否 | zh 或 en |

成功 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` | string (uuid) | |
| `username` | string | |
| `locale` | string \| null | |
| `token` | string | Sa-Token 值；随后用头 `flora-token` |

失败：`VALIDATION_ERROR`（格式不合法 / username 已存在）。不要用上游 SQL 原文当 message。

### `POST /api/v1/auth/login`

请求：`username`、`password`（必填）。

成功 `data`：同 register（`user_id` / `username` / `locale` / `token`）。

失败：用户名或密码错误 → 统一 `UNAUTHORIZED`（不暴露「用户是否存在」）。

### `POST /api/v1/auth/logout`

需登录。成功：`ok=true`，`data` 可为 `{}`。登出后旧 token 失效。

### `GET /api/v1/auth/me`

需登录。成功 `data`：

| 字段 | 类型 |
| --- | --- |
| `user_id` | string (uuid) |
| `username` | string |
| `locale` | string \| null |

无会话 → `UNAUTHORIZED`。

### 鉴权闸门（WP4 继承）

- Sa-Token 拦截：`/api/v1/**`
- 放行：`/api/v1/auth/register`、`/api/v1/auth/login`、`/actuator/**`
- 未登录访问其它 `/api/v1/**`（含尚无控制器的 `/api/v1/jobs/submit` 路径，若框架先 401/业务信封则验收信封；若先 404，则以 **已配置的拦截路径列表 + /me 行为** 为准，并在 PR 说明）——优先保证：有控制器的受保护接口与 **拦截注册表包含 `/api/v1/jobs/**`**
- HTTP 状态码：本 WP 允许 401 或 200+`ok=false` 二选一，但必须在实现里 **统一一种**，并在 PR / runbook 写明；`error_code` 字符串合同不变。

## 状态机

无 job/钱包状态机。会话生命周期：

```
匿名 ──register/login──► 已登录（Redis 会话）──logout / 超时──► 匿名
```

超时沿用现有配置默认 `FLORA_TOKEN_TIMEOUT`（2592000 秒），不要静默改默认值。

## 验收

- [ ] 存在 `docs/engineering/feature-briefs/2026-10-24-wp3-auth-users.md`，与本 brief 一致（可微调措辞，不可改表列 / 不可改 error_code 名 / 不可改 BCrypt+Sa-Token+Redis 锁定）
- [ ] `feature-briefs/README.md` 有 WP3 行
- [ ] Flyway `V1__create_users.sql` 仅建 users，列与 data-model 一致
- [ ] `POST /api/v1/auth/register` 成功写入 BCrypt 哈希；库中看不到明文密码
- [ ] `POST /api/v1/auth/login` 返回 `user_id` + token；Redis 中可见会话（或等价可观测：logout 后 token 失效）
- [ ] `GET /api/v1/auth/me` 带 `flora-token` 返回同一 `user_id`
- [ ] 无 token / 坏 token 调 `/api/v1/auth/me` → `error_code=UNAUTHORIZED`，message 为友好短句（非堆栈）
- [ ] 非法 username/password 格式 → `VALIDATION_ERROR`
- [ ] 重复 username → `VALIDATION_ERROR`（或明确同码）；无 SQL 原文泄漏
- [ ] 登录错误凭证 → `UNAUTHORIZED`（不区分「无此用户」）
- [ ] logout 后旧 token 再调 `/me` → `UNAUTHORIZED`
- [ ] Sa-Token 拦截路径覆盖 `/api/v1/**`，放行名单正确；配置或代码中显式包含对 `/api/v1/jobs/**` 的保护意图（供 WP4）
- [ ] api 仍能 `/actuator/health` → 200；不要求 worker 改认证
- [ ] 无真实密钥入库；`application*.yml` 里密码/token 签名均为环境变量占位
- [ ] 未实现 Job/MQ/钱包/Anytoken/TOS/前端托管改打 API/Admin
- [ ] 未改 `docs/content/**`；未引入 NewAPI / 双账本 / 上帝类（单文件数千行协调器）
- [ ] `runbooks/local-dev.md` 含 Postgres+Redis 前置、迁移、至少一组 curl 验收命令
- [ ] PR 描述引用本 brief 路径；列出验收自勾；写明故意没做的非范围
- [ ] 分支 `feat/wp3-auth-users`；PR base = `main`；不 force-push 他人分支

## 非目标

- 不在本任务证明 job 状态机或出图闭环
- 不把「能登录」写成「托管 flora 已可用」
- 不做社交登录全家桶与企业 IdP
- Admin 不是本任务

## 给 Minimax 的额外约束

- **先读再写**：DEVELOPMENT.md（WP3 节）、TECH_STACK.md、SECURITY.md、data-model/README.md、data-model/errors.md、i18n-and-errors.md、api/gateway-contract.md（信封）、ADR 0002 / 0005、本 brief、现有 `api/` 骨架与 application.yml。
- **只在 api 进程做认证**；不要改 worker 业务去登录。
- 分层：Controller 不写 SQL；Application 不依赖 HTTP 类型；禁止 `AuthCenterService` 式大类。
- 中文为主写 brief / 注释；代码标识符英文；JSON 字段 snake_case；`error_code` 大写蛇形。
- 新的超时 / 重试 / 限流 / 密码复杂度加码：除非写进本 brief，否则禁止静默加。
- 本地依赖：文档化 Postgres + Redis；可选最小 deps compose；不要假装「仍只需 JDK」。
- 做完用简短中文列：改了哪些模块、迁移了什么、curl 怎么验、故意没做哪些（Job/钱包/OAuth/前端）。
- 密钥、真实 Redis 密码、生产库连接串不要写进文档样例；用占位符。

## 分支 / Commit / PR 指令

```bash
git fetch origin
git checkout main
git pull origin main
git checkout -b feat/wp3-auth-users
# 1) 落入 brief + README 索引
# 2) 实现 api 认证 + Flyway + 配置 + runbook
git add docs/engineering/feature-briefs api pom.xml docs/engineering/runbooks docs/engineering/data-model/README.md
# 若加了 deps compose / .gitignore，一并 add；不要 add 密钥文件
git commit -m "WP3: Sa-Token 认证与 users 表（注册/登录/会话）"
git push -u origin feat/wp3-auth-users
```

开 PR → `main`：

- 标题：`WP3: 认证与用户（Sa-Token + users）`
- 正文必须含：brief 路径 `docs/engineering/feature-briefs/2026-10-24-wp3-auth-users.md`、做了什么、验收自勾、故意没做（无 Job/MQ/钱包/Anytoken/前端/OAuth/Admin）、本地如何起 Postgres/Redis 与 curl

**不要自行 merge**；等对照 brief 严格验收后再合。

## 环境变量提示（写入 runbook，勿填真实值）

```bash
export FLORA_API_PORT=8080
export FLORA_TOKEN_NAME=flora-token
export FLORA_TOKEN_TIMEOUT=2592000
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/flora
export SPRING_DATASOURCE_USERNAME=flora
export SPRING_DATASOURCE_PASSWORD=replace-me
export SPRING_DATA_REDIS_HOST=127.0.0.1
export SPRING_DATA_REDIS_PORT=6379
# 若 Redis 要密码：SPRING_DATA_REDIS_PASSWORD=replace-me
```

## curl 验收草稿（实现者可微调端口，须写进 runbook）

```bash
# 注册
curl -s -X POST http://127.0.0.1:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password1","locale":"zh"}'
# 登录 → 记下 token
curl -s -X POST http://127.0.0.1:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password1"}'
# me
curl -s http://127.0.0.1:8080/api/v1/auth/me -H "flora-token: $TOKEN"
# 无 token → UNAUTHORIZED
curl -s http://127.0.0.1:8080/api/v1/auth/me
```
