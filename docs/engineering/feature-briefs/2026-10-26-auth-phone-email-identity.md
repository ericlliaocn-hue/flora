# Feature brief：手机 / 邮箱身份模型（注册 · 登录 · OTP · 换绑）

- 日期：2026-10-26
- 作者：cavas / pilipala engineering
- 对应 WP / 里程碑：WP17 / 认证身份升级（承接并取代 WP3 的 username 登录产品面）
- 状态：已确认（待实现）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax（上方面包屑硬约束一并保留）。不要摘要。

本任务落地：**手机号或邮箱** 作为唯一登录身份；密码登录与验证码登录并存；换绑必须校验新值未被占用；短信走可插拔供应商（**默认短信宝**，阿里云为备选），邮件走自建 SMTP。

> **与 WP3 关系**：保留 Sa-Token + BCrypt + Redis 会话 + 公共信封 + `pilipala-token` 头。废弃「仅 username + password」产品模型。实现前先读 [2026-10-24-wp3-auth-users.md](./2026-10-24-wp3-auth-users.md) 作代码基线，再按本 brief 改列与 API。

## 背景

WP3 已落地 `users` + 注册/登录/登出/`me`，登录键为 `username`。产品方向已改为：

- 只能用 **手机号或邮箱** 注册 / 登录（禁止 username 注册登录）
- 手机、邮箱各自全局唯一，可换绑
- 密码与验证码均可登录
- 注册时手机或邮箱填一个即可，之后可再绑另一个

线上 `/api/v1/auth/*` 与前端登录 UI 需按本 brief 重做；本 brief **同时写实现范围**（api + 必要 web 对接 + data-model），不是 docs-only。

## 必须遵守

- [ADR 0002](../adr/0002-java-spring-business-api.md)：业务 API = Java 21 + Spring Boot 3；认证 = Sa-Token
- [ADR 0005](../adr/0005-friendly-errors-vs-raw-upstream.md)：C 端 `error_code` + 友好 `message`；禁止异常原文出站
- [ADR 0006](../adr/0006-mybatis-plus-persistence.md)：持久化走 MyBatis-Plus（与 TECH_STACK 一致）
- [TECH_STACK.md](../TECH_STACK.md)、[SECURITY.md](../SECURITY.md)：密钥只走环境变量
- [data-model/README.md](../data-model/README.md)、[data-model/errors.md](../data-model/errors.md)：先改文档再改代码
- [CONTRIBUTING.md](../CONTRIBUTING.md)：只做 brief 范围；不改 `docs/content/**`

## 本 WP 拍板（已决定，禁止再猜）

| 项 | 锁定值 |
| --- | --- |
| 登录身份 | **仅** `phone` 或 `email`；禁止 username 注册 / 登录 |
| 唯一性 | `phone` UNIQUE（可空）；`email` UNIQUE（可空）；同一值最多绑一个用户 |
| 注册 | 手机 **或** 邮箱二选一必填（XOR：至少一个，允许稍后绑另一个） |
| 注册凭证 | **必须设密码**（≥ 8）；注册前必须完成对应渠道 OTP 校验（防垃圾号） |
| 登录 | (A) phone\|email + password；(B) phone\|email + OTP |
| 换绑 | 登录态 + 新值 OTP 校验；**新 phone/email 若已被其他用户占用 → 失败，禁止抢绑** |
| 密码修改 | 登录态 + 旧密码 + 新密码；成功后可选踢掉其他会话（本 WP：**改密后保留当前会话，不强制全端登出**） |
| 密码找回 | 本 WP **做**：phone\|email + OTP → 设新密码（无需旧密码） |
| 密码哈希 | BCrypt strength 10（`spring-security-crypto` only，不启整链 Spring Security） |
| 会话 | Sa-Token；`loginId` = `users.id` UUID 字符串；头 `pilipala-token`；Redis 存会话 |
| OTP 存储 | **仅 Redis**（不建 OTP 表）：key / TTL / 次数见下 |
| OTP 长度 | 6 位数字 |
| OTP TTL | 300 秒 |
| 同一目的地重发冷却 | 60 秒 |
| 同一目的地发送上限 | 每小时 10 次；每日 20 次（Redis 计数） |
| 校验失败上限 | 同一 OTP 会话最多 5 次错误，超限作废并 `VALIDATION_ERROR` |
| 手机规范化 | 中国大陆 `1[3-9]\d{9}`；入库存 **E.164**：`+86` + 11 位。入参可带或不带 `+86` / `86`，服务端归一 |
| 邮箱规范化 | `trim` + **小写**；基本格式校验（含 `@`，本 WP 不做 MX 探测） |
| 短信 | 接口抽象 `SmsSender`；`PILIPALA_SMS_PROVIDER` = `smsbao`（默认）\| `aliyun` \| `logging`（仅本地） |
| 邮件 | **自建 SMTP**（JavaMailSender 或等价）；接口抽象 `EmailSender`；非第三方 ESP |
| `username` 列 | 改为 **可空**；**不再用于登录**；新注册不再写入 username（或写入 null）。展示名另议，本 WP 不新增 `display_name` |
| `role` | 保留；WP11 admin bootstrap：改为支持 `pilipala.admin.bootstrap-phones` / `pilipala.admin.bootstrap-emails`（CSV）；旧 `bootstrap-usernames` 可双读一期后删除（写进配置注释） |
| API 前缀 | `/api/v1/auth/*` |
| 信封 | `{ ok, data, error_code, message }` snake_case |
| 新错误码 | 仅新增 `OTP_RATE_LIMITED`（见 errors.md）；占用冲突、格式错误、OTP 错/过期 → `VALIDATION_ERROR`；未登录 → `UNAUTHORIZED`；登录密码错 → `UNAUTHORIZED`（不暴露用户是否存在） |

### OTP Redis key 约定（锁定）

```
pilipala:otp:{purpose}:{normalized_dest}     → code + meta（TTL 300s）
pilipala:otp:cooldown:{dest}                 → 1（TTL 60s；按目的地共享，切换 login/register 不能绕过）
pilipala:otp:rate:h:{dest}                   → count（TTL ~1h）
pilipala:otp:rate:d:{dest}                   → count（TTL 到次日 0 点或 24h）
```

`purpose` ∈ `register` | `login` | `rebind_phone` | `rebind_email` | `reset_password`

发送前：冷却未过 → `OTP_RATE_LIMITED`；小时/日超限 → `OTP_RATE_LIMITED`。  
发送前还须按 purpose 核对账号：`login` / `reset_password` 账号不存在 → `AUTH_ACCOUNT_NOT_FOUND`；`register` 已注册 → `AUTH_IDENTITY_ALREADY_REGISTERED`；`rebind_*` 被他人占用 → `AUTH_IDENTITY_OCCUPIED`。  
校验：`login` ↔ `register` 可互认同一 `dest` 上未消费的有效码（短信正文相同）。

### 短信 / 邮件配置（环境变量占位，禁止提交真值）

```bash
PILIPALA_SMS_PROVIDER=smsbao

# 短信宝（生产默认；USERNAME 示例 ericlliao，真实 ApiKey 仅环境变量）
PILIPALA_SMS_SMSBAO_USERNAME=ericlliao
PILIPALA_SMS_SMSBAO_API_KEY=
PILIPALA_SMS_SMSBAO_GOODS_ID=
PILIPALA_SMS_SMSBAO_CONTENT_TEMPLATE=【pilipala】您的验证码是{code}，5分钟内有效。若非本人操作请忽略。
PILIPALA_SMS_SMSBAO_BASE_URL=https://api.smsbao.com/sms

# 阿里云 SMS（可选）
PILIPALA_SMS_ALIYUN_ACCESS_KEY_ID=
PILIPALA_SMS_ALIYUN_ACCESS_KEY_SECRET=
PILIPALA_SMS_ALIYUN_SIGN_NAME=
PILIPALA_SMS_ALIYUN_TEMPLATE_REGISTER=
PILIPALA_SMS_ALIYUN_TEMPLATE_LOGIN=
PILIPALA_SMS_ALIYUN_TEMPLATE_REBIND=
PILIPALA_SMS_ALIYUN_TEMPLATE_RESET=
PILIPALA_SMS_ALIYUN_ENDPOINT=dysmsapi.aliyuncs.com

# Self-hosted SMTP
PILIPALA_MAIL_HOST=
PILIPALA_MAIL_PORT=587
PILIPALA_MAIL_USERNAME=
PILIPALA_MAIL_PASSWORD=
PILIPALA_MAIL_FROM=
PILIPALA_MAIL_STARTTLS=true
```

短信宝正文须含签名括号（如 `【pilipala】`），`{code}` 占位；阿里云模板变量至少含验证码。runbook 说明配置，禁止提交真实 ApiKey。

## 本 WP 不拍板（禁止发明）

- OAuth / 社交登录 / Passkey / MFA TOTP
- 国际手机号（非 +86）完整支持（本 WP 仅大陆号；其他格式 `VALIDATION_ERROR`）
- Cookie 会话、跨域 Cookie
- 改密后强制全端登出、设备管理列表
- `display_name` / 头像资料完善（另 brief）
- 前端像素级视觉大改（本 WP 只要求登录/注册/换绑/找回 **能打通新 API**；样式跟现有 Studio 即可）
- Admin 用户搜索从 username 迁到 phone/email 的完整 UI 文案（api 侧 me/admin 返回字段改对即可，Admin 大改另开）

## 目标

1. `users` 表具备可空唯一 `phone` / `email`，CHECK 至少一个非空；`username` 可空且不再登录。
2. 可 OTP 校验后 register（phone XOR email + password）→ BCrypt 入库 → 返回 token。
3. 可用密码或 OTP 登录 → Redis 会话 → 稳定 `user_id`。
4. 登录用户可换绑 phone 或 email；**占用则失败**。
5. 可改密、可 OTP 找回密码。
6. `GET /me` 返回脱敏 phone/email（中间打码），不含登录用 username。
7. 短信走 `SmsSender` 多通道（默认短信宝）；邮件走自建 SMTP；本地短信设 `PILIPALA_SMS_PROVIDER=logging`，**生产禁止 logging 与明文 OTP 日志**。
8. data-model / errors / feature-briefs 索引已更新；runbook 含 curl 验收。

## 范围

允许新增 / 修改：

| 路径 | 动作 |
| --- | --- |
| `docs/engineering/feature-briefs/2026-10-26-auth-phone-email-identity.md` | 新增（本文件） |
| `docs/engineering/feature-briefs/README.md` | 索引加 WP17 行 |
| `docs/engineering/data-model/README.md` | `users` 列改为本 brief |
| `docs/engineering/data-model/errors.md` | 登记 `OTP_RATE_LIMITED` |
| `docs/engineering/runbooks/local-dev.md` | OTP / 短信宝·阿里云·logging / SMTP 占位与 curl |
| Flyway 新脚本（下一序号，如 `V10__users_phone_email.sql`） | 改 `users`：加 phone/email、放宽 username、CHECK、索引；数据迁移见下 |
| `api/.../auth/**` | 控制器、用例、OTP 服务、SmsSender/EmailSender、仓储 |
| `api` 配置 / 依赖 | 阿里云 SMS SDK 或官方 HTTP 客户端；`spring-boot-starter-mail` |
| Sa-Token 放行名单 | 新公开接口 |
| `web/` 认证相关 | `use-auth-store` / `pilipala-auth` / 登录注册弹层：改为 phone\|email + 密码/验证码；去掉 username 注册登录入口 |
| WP11 bootstrap 配置 | phones/emails 双读 |
| i18n 种子（若 WP9 表已在） | 为新码与关键校验文案补 zh/en（或静态 Map 与现网 WP3 临时方案一致，二选一写进 PR 说明） |

### 建议包结构（可微调名，职责必须拆开）

```
art.pilipala.api.auth
  web/           AuthController, 全局异常信封
  application/   Register / LoginPassword / LoginOtp / SendOtp / RebindPhone / RebindEmail / ChangePassword / ResetPassword / Me / Logout
  domain/        User, IdentityNormalizer
  infra/         UserMapper, OtpRedisStore, SmsSenderConfiguration, SmsBaoSmsSender, AliyunSmsSender, LoggingSmsSender, SmtpEmailSender, PasswordHasher, SaTokenConfig
```

## 非范围

- Job / wallet / Anytoken / TOS / Rabbit
- OAuth / 社交 / MFA
- 新建第二套用户表
- 把 Spring Security FilterChain 整链拉起替代 Sa-Token
- `docs/content/**` 公开站点叙事大改
- 真实 AK/SK、SMTP 密码入库

## 数据表字段

### `users`（本 WP 后）

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK | Sa-Token loginId |
| `username` | TEXT | YES | UNIQUE 仍保留（多 NULL 在 PG 不冲突） | **不再登录**；新用户为 null |
| `phone` | TEXT | YES | UNIQUE | E.164，如 `+8613800138000` |
| `email` | TEXT | YES | UNIQUE | 小写 |
| `password_hash` | TEXT | NO | | BCrypt |
| `locale` | TEXT | YES | | zh / en |
| `role` | TEXT | NO | DEFAULT `user`，CHECK ∈ user\|admin | 保留 |
| `created_at` | TIMESTAMPTZ | NO | DEFAULT now() | |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

**表级 CHECK**：`phone IS NOT NULL OR email IS NOT NULL`

### Flyway 语义

1. `ALTER` 加 `phone` / `email`（可空 + UNIQUE）。
2. `username` drop NOT NULL（保留 UNIQUE）。
3. 加 CHECK（至少一个身份）。
4. **存量迁移（产品未正式放量也可执行）**：
   - 若存在仅 username 行：本 WP **要求**迁移脚本不删除行；若无法自动得到 phone/email，允许暂时 **不满足 CHECK 的旧行** 吗？  
   - **锁定**：迁移分两步——先加列与唯一索引；用会话/运维 SQL 为旧账号补绑；最后再 `ADD CHECK`。若库中 **零** 业务用户，可直接 CHECK。PR 中探测：`SELECT count(*) FROM users`，零行则同迁移加 CHECK；有行则 CHECK 放后续 hotfix 并在 PR 标明。
5. 禁止 DROP `username` 列（本 WP）。

## API

公共信封同 WP3。

### 脱敏规则

- phone：`+86****1234`（保留国家码与后 4 位）
- email：`ab***@example.com`（@ 前保留最多 2 字符）

### `POST /api/v1/auth/otp/send`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `channel` | string | 是 | `sms` \| `email` |
| `destination` | string | 是 | 手机或邮箱原文 |
| `purpose` | string | 是 | 见上 purpose 枚举 |

成功：`data: { "expires_in": 300, "cooldown": 60 }`（**不**回显验证码）。  
失败：格式 → `VALIDATION_ERROR`；限流 → `OTP_RATE_LIMITED`。  
`purpose=rebind_*` / 改密类需登录的，本接口：**send 本身对 register/login/reset_password 公开；rebind_* 必须登录**（未登录 `UNAUTHORIZED`）。

### `POST /api/v1/auth/register`

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `phone` | string | 与 email 二选一 |
| `email` | string | 与 phone 二选一 |
| `password` | string | 是 ≥8 |
| `otp` | string | 是 |
| `locale` | string | 否 zh\|en |

成功 `data`：`user_id`, `phone`（脱敏或 null）, `email`（脱敏或 null）, `locale`, `role`, `token`。  
失败：OTP 错/过期、身份已注册、格式 → `VALIDATION_ERROR`。

### `POST /api/v1/auth/login`

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `phone` / `email` | string | 二选一 |
| `password` | string | 与 otp 二选一 |
| `otp` | string | 与 password 二选一 |

成功：同 register 的 data 形状。  
密码错 / 用户不存在：统一 `UNAUTHORIZED`。OTP 错：`VALIDATION_ERROR`。

### `POST /api/v1/auth/password/change`（需登录）

请求：`old_password`, `new_password`（≥8）。成功 `{}`。

### `POST /api/v1/auth/password/reset`

请求：`phone` 或 `email`、`otp`、`new_password`。成功可直接发 token（**锁定：重置成功后自动登录并返回 token**）。

### `POST /api/v1/auth/rebind/phone`（需登录）

请求：`new_phone`, `otp`（发往 **新** 手机，purpose=`rebind_phone`）。  
成功：更新 phone。  
**新号已被其他用户占用 → `VALIDATION_ERROR`，文案明确「已被占用」，不得静默改到别的用户。**

### `POST /api/v1/auth/rebind/email`（需登录）

对称；purpose=`rebind_email`；占用同样拒绝。

### `POST /api/v1/auth/logout` / `GET /api/v1/auth/me`

logout 同 WP3。  
me：`user_id`, `phone`（脱敏\|null）, `email`（脱敏\|null）, `locale`, `role`。**不要**再把 username 当登录名返回（若列非空可额外 `username` 字段标注废弃，或直接省略——**锁定：响应省略 username**）。

### 鉴权放行

拦截 `/api/v1/**`；额外放行：

- `/api/v1/auth/register`
- `/api/v1/auth/login`
- `/api/v1/auth/otp/send`（控制器内对 rebind purpose 再鉴权）
- `/api/v1/auth/password/reset`
- `/actuator/**`

`otp/send` 若实现为「网关全放行 + 方法内鉴权」，必须在验收写明，且 rebind purpose 无 token 必 `UNAUTHORIZED`。

## 状态机

```
匿名 ──register / login / reset──► 已登录 ──logout / 超时──► 匿名
已登录 ──rebind（新值 OTP + 未占用）──► 已登录（身份列更新）
已登录 ──change password──► 已登录
```

OTP：`send → (cooldown) → verify once → consume`；失败计数超限 → 作废。

## 验收

- [ ] brief 落盘；README 索引有 WP17 行；data-model `users` 与 errors 含 `OTP_RATE_LIMITED`
- [ ] Flyway 加 phone/email 唯一 + CHECK 策略符合「空库直接 CHECK / 有存量先补绑」
- [ ] 注册：仅手机或仅邮箱 + OTP + 密码成功；两者都空或都填且冲突规则按 XOR（**锁定：禁止同时填 phone 与 email 注册**，多填 → `VALIDATION_ERROR`）
- [ ] 重复 phone/email 注册 → `VALIDATION_ERROR`
- [ ] 密码登录 / OTP 登录均返回 token；`me` 脱敏正确
- [ ] 错误密码不泄露用户是否存在（`UNAUTHORIZED`）
- [ ] 换绑到已被占用的 phone/email → 失败且原绑定不变（**硬验收**）
- [ ] 换绑到空闲号/邮箱 + 正确 OTP → 成功；旧值释放后可被他人注册
- [ ] OTP 冷却与小时/日限触发 `OTP_RATE_LIMITED`
- [ ] OTP 错误 5 次后作废
- [ ] 改密、重置密码成功；重置返回 token
- [ ] 生产路径短信走短信宝（或 aliyun 配置），邮件走 SMTP；`logging` 仅本地且测试注明
- [ ] 前端去掉 username 登录/注册；可完成注册登录换绑主路径
- [ ] 无真实密钥入库；未做 OAuth/Job/钱包顺手改
- [ ] PR 引用本 brief；分支 `feat/wp17-auth-phone-email`；**不要自行 merge**

## 非目标

- 不做社交登录全家桶与企业 IdP
- 不做国际号段完整校验产品化
- Admin 画布与运营大改不是本任务

## 给 Minimax 的额外约束

- **先读再写**：本 brief、WP3 brief、data-model users/errors、SECURITY、现有 `AuthController` / web auth store、ADR 0002/0005/0006。
- Controller 不写 SQL；Application 不依赖 HTTP 类型；禁止上帝类。
- 中文注释 / brief；代码标识英文；JSON snake_case。
- 禁止静默加额外限流/密码规则；要加先改 brief。
- 短信与 SMTP 必须接口可替换（`SmsSender` + `EmailSender`）；单测可用 fake sender。
- 做完用中文列：改了哪些模块、迁移步骤、curl、故意没做的。

## 分支 / Commit / PR

```bash
git fetch origin && git checkout main && git pull
git checkout -b feat/wp17-auth-phone-email
# docs + Flyway + api auth + web auth
git commit -m "WP17: 手机/邮箱身份（OTP + 换绑占用校验）"
git push -u origin feat/wp17-auth-phone-email
```

PR 标题：`WP17: 手机/邮箱身份（阿里云短信 + 自建邮件 + 换绑）`  
正文：brief 路径、验收自勾、非范围、本地如何开 local OTP。

## curl 草稿（写入 runbook）

```bash
# 发注册 OTP（local 看 api 日志里的码）
curl -s -X POST http://127.0.0.1:8080/api/v1/auth/otp/send \
  -H 'Content-Type: application/json' \
  -d '{"channel":"sms","destination":"13800138000","purpose":"register"}'

curl -s -X POST http://127.0.0.1:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"phone":"13800138000","password":"password1","otp":"123456","locale":"zh"}'

# 换绑占用失败用例：用户 A 占有邮箱后，用户 B rebind 同一邮箱 → VALIDATION_ERROR
```
