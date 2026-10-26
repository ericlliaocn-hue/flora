# Feature brief：认证弹层 Concept B + 邮箱通道库表开关

- 日期：2026-10-26
- 作者：cavas / pilipala engineering
- 对应 WP / 里程碑：WP18 / 认证产品面（承接 WP17 API，重做 UI + 动态关邮箱）
- 状态：已确认（待实现）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax（上方面包屑硬约束一并保留）。不要摘要。

本任务落地：把 Studio 登录/注册弹层改成 **Concept B 悬浮丝带** 视觉；**去掉顶栏登录/注册 Tab**，用右下角小字切换；默认 **只暴露手机号**（邮箱注册/登录入口隐藏）；邮箱是否可走用 **数据库字段动态配置**，以后可开可关，无需发版前端硬编码。顺带修邮件 OTP「未配置仍返回成功」假成功。

> **与 WP17 关系**：API 契约（`/api/v1/auth/*`、手机 E.164、OTP Redis、密码/OTP 登录、换绑占用）**保持**。本 WP 只加公开特性开关 + 前端弹层重做 + 邮件发送失败显式错误。实现前先读 [2026-10-26-auth-phone-email-identity.md](./2026-10-26-auth-phone-email-identity.md)。

## 背景

WP17 已上线手机 OTP（短信宝真机通过）。邮箱 SMTP 未配，且 `SmtpEmailSender` 在 host 空时 **跳过仍返回 ok**，前端仍展示「邮箱」选项会误导用户。现网 `auth-modal.tsx` 为 Ant Design Tabs + Radio（手机/邮箱），视觉偏后台表单，与 Studio / Concept D 品牌不一致。产品已锁定视觉 **Concept B**，并要求邮箱入口可库表动态开关。

视觉参考（实现对照布局与层次，**文案以本 brief 清单为准，勿抄图中糊字**）：

- `docs/engineering/feature-briefs/assets/wp18-auth-ui/concept-b-register-v3.jpg`（仓库落地时放入同路径；粘贴实现时可向 cavas 索取）
- `docs/engineering/feature-briefs/assets/wp18-auth-ui/concept-b-login-v3.jpg`

## 必须遵守

- [ADR 0002](../adr/0002-java-spring-business-api.md)、[ADR 0005](../adr/0005-friendly-errors-vs-raw-upstream.md)、[ADR 0006](../adr/0006-mybatis-plus-persistence.md)
- [TECH_STACK.md](../TECH_STACK.md)、[SECURITY.md](../SECURITY.md)
- [data-model/README.md](../data-model/README.md)、[data-model/errors.md](../data-model/errors.md)：先改文档再改代码
- [CONTRIBUTING.md](../CONTRIBUTING.md)：只做 brief 范围；不改 `docs/content/**`

## 本 WP 拍板（已决定，禁止再猜）

| 项 | 锁定值 |
| --- | --- |
| 视觉 | **Concept B 悬浮丝带**：深色毛玻璃窄卡 + 背后大号 Concept D infinity 丝带光晕（可用 CSS/轻量 SVG/锁定 PNG 资源，禁止另画成双 p 字母标） |
| 主色 | Coral `#E8867A`（主按钮、强调链、丝带高光）；文字近白；卡面半透明深色 |
| 登录 ↔ 注册切换 | **禁止**顶栏 Tabs / segmented「登录\|注册」。用卡片 **右下角小字链接** 切换 |
| 注册页右下 | 文案精确：`已有账号？去登录` |
| 登录页右下 | 文案精确：`没有账号？去注册` |
| 身份通道默认 | UI **只展示手机号**；不展示邮箱 Radio/入口 |
| 邮箱动态开关 | 库表配置；默认 **关**；打开后 UI 才出现邮箱选项；API 也按开关拒绝 |
| 开关粒度 | 两把独立布尔：`auth.email_login_enabled`、`auth.email_register_enabled`（均可默认 `false`）；另可共用读模型，见下表 |
| 登录方式芯片 | 仅两种：`密码登录` / `验证码登录`。禁止出现「登录方式登录」「短信登录」等糊文案 |
| OTP 按钮文案 | 精确：`获取验证码`。禁止：`获取短信`、`发送短信`、`获取手机号` |
| 字段文案 | `手机号`（+86 / placeholder `11位手机号`）、`密码`（`至少8位`）、`验证码`（`6位数字`） |
| 注册主按钮 | `开始创作` |
| 登录主按钮 | `登录` |
| 忘记密码 | 登录·密码模式下保留右下小链 `忘记密码`（可先跳到验证码登录或现有 reset 流；本 WP **打通** `purpose=reset_password` 发码 + `/password/reset` 最小 UI，不做花活） |
| 禁止文案 | 全站认证弹层与相关 i18n：**不得**出现 `身份证`、`证件`、`实名`、证件号采集 |
| Logo | 现有 `PilipalaLogo` img 资源（Concept D lockup）；不要手绘 path |
| 邮件发送失败 | `SmtpEmailSender`：未配置 host **或** SMTP 抛错 → **必须**抛与短信一致的 `PROVIDER_UNAVAILABLE`（或等价 AuthException），**禁止**静默 return；`OtpService` 已是先发送后写 Redis，保持 |
| 邮箱关时 API | `channel=email` 的 `otp/send`、以及 register/login 带 `email`：返回 `VALIDATION_ERROR`（或 `FORBIDDEN` 二选一——**锁定 `VALIDATION_ERROR`**，message 友好「暂不支持邮箱」），不写 Redis OTP |

### 文案白名单（实现必须用这些字面量；i18n key 映射到它们）

| key 建议 | zh |
| --- | --- |
| auth.registerTitle | 注册 |
| auth.loginTitle | 登录 |
| auth.registerSubtitle | 手机号注册 · 预付费积分生图 |
| auth.loginSubtitle | 手机号登录 |
| auth.phone | 手机号 |
| auth.phonePlaceholder | 11位手机号 |
| auth.password | 密码 |
| auth.passwordPlaceholder | 至少8位 |
| auth.otp | 验证码 |
| auth.otpPlaceholder | 6位数字 |
| auth.getOtp | 获取验证码 |
| auth.passwordLogin | 密码登录 |
| auth.otpLogin | 验证码登录 |
| auth.startCreate | 开始创作 |
| auth.login | 登录 |
| auth.gotoLogin | 已有账号？去登录 |
| auth.gotoRegister | 没有账号？去注册 |
| auth.forgotPassword | 忘记密码 |
| auth.email（仅开关开时） | 邮箱 |

## 本 WP 不拍板（禁止发明）

- 国际手机号、OAuth、Passkey、身份证/实名
- 新建完整 Admin 配置后台大改（本 WP 可用 SQL seed + Admin **最小** PATCH；若 Admin UI 来不及，允许仅 SQL/API 改开关，PR 注明）
- 邮件 SMTP 运维搭建（host/账号仍走 env；本 WP 只修假成功）
- 像素级还原生成图里的每一个光斑；以「毛玻璃卡 + 丝带背景 + coral CTA + 右下切换」为验收

## 目标

1. 打开 Studio 登录/注册弹层：Concept B 观感；无顶栏 Tab；右下角切换登录/注册。
2. 默认看不到邮箱入口；手机号注册/登录/OTP 可用。
3. 改库表开关后（不必重新构建前端静态包），公开配置接口返回新值，刷新页面后邮箱入口出现/消失。
4. 开关关闭时，即便手工调 API 发邮箱 OTP / 邮箱注册登录，也被拒绝。
5. SMTP 未配置或发送失败时，客户端收到明确错误，不再 `ok:true` + Redis 已有码。
6. data-model / feature-briefs 索引已更新。

## 范围

| 路径 | 动作 |
| --- | --- |
| `docs/engineering/feature-briefs/2026-10-26-auth-ui-ribbon-email-flags.md` | 新增（本文件） |
| `docs/engineering/feature-briefs/assets/wp18-auth-ui/*` | 新增参考图（可选，建议入库） |
| `docs/engineering/feature-briefs/README.md` | 索引加 WP18 |
| `docs/engineering/data-model/README.md` | 登记 `app_runtime_setting` |
| Flyway `V11__app_runtime_setting.sql`（序号若冲突则下一号） | 建表 + seed 两把邮箱开关 false |
| `api/...` | 读设置仓储；公开 `GET /api/v1/auth/features`；send/register/login 校验开关；修 `SmtpEmailSender` |
| `api/.../admin/**`（最小） | `PATCH /api/v1/admin/runtime-settings/{key}` 或批量 PATCH（需 admin） |
| `web/src/components/auth/auth-modal.tsx` 及样式 | 按 Concept B 重做；去掉 Tabs；右下切换；读 features |
| `web` i18n / 文案 | 按白名单 |
| `CHANGELOG.md` | 一行 |

## 非范围

- 换绑 UI 大改、个人资料页
- 搭建真实 SMTP 服务器 / 写入生产 `PILIPALA_MAIL_*`（运维另做）
- 改 WP17 手机规范化 / OTP TTL / 短信宝适配器逻辑（除非为配合开关）
- `docs/content/**`

## 数据表字段

### 新表 `app_runtime_setting`

| 字段 | 类型 | 空 | 约束 | 说明 |
| --- | --- | --- | --- | --- |
| `key` | TEXT | NO | PK | 点分命名 |
| `value` | TEXT | NO | | 布尔存 `true`/`false` 小写字符串；本 WP 只用布尔 |
| `updated_at` | TIMESTAMPTZ | NO | DEFAULT now() | |

**Seed（迁移内 INSERT）：**

| key | value |
| --- | --- |
| `auth.email_login_enabled` | `false` |
| `auth.email_register_enabled` | `false` |

无第二套用户表。禁止把开关只写在 `application.yml` 而不入库（env 可作为 **缺省覆盖** 可选：若实现双读，**锁定优先级：DB > env > 代码默认 false**；无 env 也可，但 DB 必须有）。

## API

信封同 WP17。

### `GET /api/v1/auth/features`（公开，无需登录）

响应 `data`：

```json
{
  "email_login_enabled": false,
  "email_register_enabled": false
}
```

前端弹层打开时拉取（可短缓存 ≤60s 内存）；切换 tab 不必每次打。

### 现有接口行为增量

- `POST /api/v1/auth/otp/send`：`channel=email` 且对应 purpose 属于 login/register/reset 时，若对应开关关 → `VALIDATION_ERROR`。`rebind_email`：本 WP **锁定** 另读同一 `auth.email_register_enabled`（或新增 `auth.email_rebind_enabled`——**锁定复用 `auth.email_login_enabled || auth.email_register_enabled`**，任一为 true 才允许 rebind_email 发码；两者都 false 则拒绝）。
- `POST /api/v1/auth/register` 带 `email`：`auth.email_register_enabled=false` → `VALIDATION_ERROR`。
- `POST /api/v1/auth/login` 带 `email`：`auth.email_login_enabled=false` → `VALIDATION_ERROR`（与「用户不存在」一样勿泄露内部开关细节以外的东西；message 用「暂不支持邮箱登录/注册」即可）。

### Admin（最小）

- `GET /api/v1/admin/runtime-settings` → 列表或 map
- `PATCH /api/v1/admin/runtime-settings` body: `{ "auth.email_login_enabled": true, ... }` 仅允许已知 key；需 admin 角色

## 状态机

无新会话状态机。弹层 UI 状态：`mode ∈ {login, register}`；登录下 `loginMethod ∈ {password, otp}`。

## UI 结构（验收对照）

```
[毛玻璃卡]
  Logo lockup
  标题：登录 | 注册（纯标题，非 Tab）
  副标题
  （仅 features 打开时）身份：手机号 | 邮箱
  （登录）芯片：密码登录 | 验证码登录
  手机号 (+86)
  密码 和/或 验证码 +「获取验证码」
  主按钮
  右下角：去登录 / 去注册
```

背景：Studio 遮罩上可见大号丝带（卡后），不要把丝带画进卡内当小 icon 替代。

## 验收

- [ ] 默认 features 两开关 false；UI 无邮箱入口
- [ ] 注册：手机号 + 密码 + 验证码 +「获取验证码」+「开始创作」；右下「已有账号？去登录」
- [ ] 登录：密码/验证码芯片文案正确；主按钮「登录」；右下「没有账号？去注册」
- [ ] **无**顶栏登录/注册 Tab
- [ ] SQL 把 `auth.email_register_enabled=true` 后，刷新可见邮箱注册；再改回 false 消失
- [ ] 开关 false 时 curl 邮箱 otp/send → 非 ok，且 Redis 无新 email OTP
- [ ] SMTP host 空时 email otp/send → 非 ok（PROVIDER_UNAVAILABLE 或等价），非假成功
- [ ] 真机手机 OTP 回归仍可用
- [ ] 文案白名单抽检：页面源码/ i18n 无「身份证」「获取短信」「登录方式登录」
- [ ] 相关 Studio 首页打开弹层无白屏/对比度回归（主按钮在深底上可读）

## 非目标

- 实名认证、证件上传
- 第三方登录
- 邮件运维建站

## 给 Minimax 的额外约束

- 遵守 `docs/engineering/TECH_STACK.md` 与 `adr/`。
- 不改无关文件，不重写 `docs/content/`。
- 密钥占位；生产开关只通过 DB/Admin，不把 true 写进仓库 seed 以外的「开启」状态（seed 必须 false）。
- 前端不要用 `Tabs` 做登录/注册；用本地 state + 右下链接。
- 丝带资源优先复用现有 logo PNG / 轻量装饰；禁止再引入「双竖 p」旧标。
- PR 说明含：Flyway 版本号、features curl 样例、UI 截图（登录+注册各一张）。
