# Feature brief：WP9 友好错误与 i18n（`i18n_message` 表）

- 日期：2026-10-24
- 作者：pilipala engineering
- 对应 WP / 里程碑：WP9 / M2（友好错误映射与 `i18n_message` 数据库驱动词条闭环）
- 状态：已确认（待实现）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax（上方面包屑硬约束一并保留）。不要摘要。

本任务落地 **pilipala 友好错误映射与 `i18n_message` 表闭环**：通过 Flyway `V5__create_i18n_message.sql` 建立 `i18n_message` 表并灌入 stage-1 全部已登记 `error_code`（及重试/动态模板）的中英文（`zh` / `en`）种子词条；在 `api` 模块新增 `art.pilipala.api.i18n` 包（`I18nMessage`、`I18nMessageRepository`、`JdbcI18nMessageRepository`、`I18nMessageService`、`I18nDefaultCatalog`、`RequestLocaleResolver`），实现「用户偏好 `users.locale` → 请求 `Accept-Language` → `'zh'`」语言选择链与「DB `(key, locale)` → DB `(key, 'zh')` → 内置默认种子 → `error_code` 本身」缺词条安全回退链，支持有限安全占位符插值（如 `{attempt}`、`{max_attempts}`）；删除 WP3/WP4 临时硬编码类 `AuthErrorMessages` 与 `JobErrorMessages`，统一收口 `api` 全部异常处理器与任务状态查询的友好文案解析，并补齐未捕获异常兜底（严禁向 C 端泄露 SQL、堆栈或上游原文）。**严守 DEVELOPMENT.md 与 ADR 0004 / 0005：`worker` 模块零改动、不把前端静态 UI 词条搬进数据库、不做运营后台 CRUD 页面（留给 WP11）、不改前端 `web/`。**

## 背景

WP0–WP6、WP8 已合入 `main`（当前最新为 WP8 `2ca58ba`）：

- `api` 已有 `V1`（`users`）、`V2`（`job` / `job_step` / `idempotency_record` / `outbox_message`）、`V3`（`provider_task`）、`V4`（`wallet` / `wallet_hold` / `wallet_ledger`），以及火山 TOS 短期预签名读 URL（WP8）。
- 在 WP3 与 WP4 中，为了先跑通认证与任务生命周期，`api` 临时放置了两个硬编码静态类：
  - `art.pilipala.api.auth.infra.AuthErrorMessages`（Javadoc 已注明：`WP9 引入 i18n_message 表后删掉本类，改为按 locale 查表`）
  - `art.pilipala.api.job.infra.JobErrorMessages`（Javadoc 已注明：`WP9 引入 i18n_message 表后删掉本类，改为按 locale 查表`）
- 同时，当前 `JobController` / `JobExceptionHandler` / `AuthExceptionHandler` 只解析了请求头 `Accept-Language`，尚未按 `i18n-and-errors.md` 优先读取已登录用户的 `users.locale` 偏好；且 `AuthExceptionHandler` 尚未兜底未预期 `Exception`（一旦出现未捕获运行时异常，可能落回 Spring 默认 500 错误体）。
- 按「先做完后端再接前端」顺序，本 WP（WP9）完成后，后端 M1–M2 的数据库迁移（`V1`–`V5`）与 C 端接口契约即全部收口，随后即可进入前端画布接线的 WP7。

[DEVELOPMENT.md](../DEVELOPMENT.md) WP9：

> - **里程碑**：M2
> - **做什么**：上游失败分类 → 稳定 `error_code` → 按 locale 查 `i18n_message` 组装用户可见短句；原文只进 `provider_task` 与日志。码表与表结构见 [data-model/errors.md](../data-model/errors.md)。
> - **不做**：把整站静态 UI 文案搬进 DB。
> - **完成**：任一模拟上游失败，C 端看不到堆栈或供应商英文原文。

## 必须遵守

- [ADR 0004](../adr/0004-thin-worker-no-god-classes.md)：`worker` 保持瘦进程，只写稳定 `error_code` 与 `provider_task.raw_body`，**不查 `i18n_message`、不拼用户语言文案**。`worker/` 目录在本 WP **零文件改动**
- [ADR 0005](../adr/0005-friendly-errors-vs-raw-upstream.md)：
  - C 端默认响应只包含稳定 `error_code` + 友好、已本地化的短句（可带安全插值，如重试次数）
  - 上游 HTTP 状态码、原始响应体、堆栈只进 `provider_task` 与后端结构化日志
  - **严禁**把 `e.getMessage()`、SQL 报错、Spring 默认异常信息或供应商英文原文填进 C 端 `message`
- [data-model/errors.md](../data-model/errors.md)：
  - `i18n_message` 表的表名、5 个列名（`id`、`key`、`locale`、`template`、`updated_at`）、唯一约束 `UNIQUE (key, locale)`、`locale CHECK (locale IN ('zh', 'en'))` 必须逐字照抄
  - 已登记 `error_code` 枚举（共 8 个：`WALLET_INSUFFICIENT`、`JOB_NOT_FOUND`、`JOB_CANCELLED`、`JOB_FAILED`、`PROVIDER_UNAVAILABLE`、`IDEMPOTENCY_CONFLICT`、`UNAUTHORIZED`、`VALIDATION_ERROR`）是唯一合法码表，**禁止自造未登记 `error_code`**
- [i18n-and-errors.md](../i18n-and-errors.md)：
  - **两层 i18n 不混用**：前端静态 UI 走 `web/src/i18n/locales/{zh,en}.json`，数据库 `i18n_message` 只存服务端错误模板与运营可改的动态提示，**禁止把前端按钮/菜单文案搬进数据库**
  - **语言选择优先级**：
    1. 已登录用户若有 `users.locale` 偏好（`'zh'` 或 `'en'`），优先用用户偏好；
    2. 否则用请求 `Accept-Language`（`en*` → `'en'`，其余 → `'zh'`）；
    3. 缺词条回退：请求语言（如 `'en'`）→ `'zh'` → `error_code` 本身（最后手段，绝不回退到异常堆栈或上游英文原文）
- [api/gateway-contract.md](../api/gateway-contract.md)：所有失败响应保持 HTTP 200 + `{"ok": false, "data": null, "error_code": "...", "message": "..."}` 统一信封
- [CONTRIBUTING.md](../CONTRIBUTING.md)：只做 brief 范围；不改 `worker/`、`web/`、`docs/content/**` 与 `CHANGELOG.md`

## 本 WP 拍板（已决定，禁止再猜）

| 项 | 锁定值 |
| --- | --- |
| 迁移 `V5` | `api/src/main/resources/db/migration/V5__create_i18n_message.sql`：新建 `i18n_message` 表 + `UNIQUE (key, locale)` 约束，并 `INSERT` 灌入全部 8 个已登记 `error_code` 及 `JOB_FAILED.RETRY` 的 `'zh'` / `'en'` 种子行（共 18 行，`ON CONFLICT (key, locale) DO NOTHING`）。**严禁修改 `V1` / `V2` / `V3` / `V4` 已有迁移文件** |
| `i18n_message` 表结构 | 严格照抄 [data-model/errors.md](../data-model/errors.md)：<br>1) `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`<br>2) `key TEXT NOT NULL`<br>3) `locale TEXT NOT NULL CHECK (locale IN ('zh', 'en'))`（注意是 `'zh'` / `'en'`，不是 `'zh-CN'` / `'en-US'`）<br>4) `template TEXT NOT NULL`<br>5) `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`<br>6) `CONSTRAINT i18n_message_key_locale_key UNIQUE (key, locale)` |
| 种子词条内容 | 8 个基础 `error_code` 的中英文模板与现行测试断言完全对齐：<br>- `WALLET_INSUFFICIENT`：`zh`=`"账户余额不足，请先充值。"` / `en`=`"Insufficient wallet balance. Please top up first."`<br>- `JOB_NOT_FOUND`：`zh`=`"任务不存在。"` / `en`=`"The task does not exist."`<br>- `JOB_CANCELLED`：`zh`=`"任务已取消。"` / `en`=`"The task has been cancelled."`<br>- `JOB_FAILED`：`zh`=`"任务执行失败。"` / `en`=`"The task failed to run."`<br>- `PROVIDER_UNAVAILABLE`：`zh`=`"生成服务暂时不可用，请稍后重试。"` / `en`=`"The generation service is temporarily unavailable. Please try again later."`<br>- `IDEMPOTENCY_CONFLICT`：`zh`=`"同一个幂等键对应了不同的请求内容。"` / `en`=`"The same idempotency key was used with a different request."`<br>- `UNAUTHORIZED`：`zh`=`"请先登录后再试。"` / `en`=`"Please sign in first."`<br>- `VALIDATION_ERROR`：`zh`=`"请求参数不合法。"` / `en`=`"The request parameters are invalid."`<br>另加 [data-model/errors.md](../data-model/errors.md) 点名的带占位符前缀键示例：<br>- `JOB_FAILED.RETRY`：`zh`=`"生成服务异常，正在重试（第 {attempt}/{max_attempts} 次）。"` / `en`=`"Generation issue, retrying ({attempt}/{max_attempts})."` |
| 清理临时硬编码类 | **删除** `art.pilipala.api.auth.infra.AuthErrorMessages` 与 `art.pilipala.api.job.infra.JobErrorMessages`（兑现 WP3/WP4 注释承诺），所有调用方统一迁移到 `art.pilipala.api.i18n` |
| 语言决策链（`RequestLocaleResolver`） | 1) 若当前请求已有登录会话且对应 `users.locale` 非空（`'zh'` 或 `'en'`），优先返回 `users.locale`；<br>2) 否则解析 `Accept-Language` 请求头（忽略前导空白，大小写不敏感以 `en` 开头返回 `'en'`，其余返回 `'zh'`）；<br>3) 任何异常或空值一律回退 `'zh'` |
| 词条查询与回退链（`I18nMessageService`） | `resolve(String key, String locale)` 与 `resolve(String key, String locale, Map<String, Object> params)`：<br>1) 规范化 `locale` 为 `'zh'` 或 `'en'`；<br>2) 先查 `I18nMessageRepository.findByKeyAndLocale(key, normalizedLocale)`；<br>3) 若未命中且 `normalizedLocale` 为 `'en'`，再查 `findByKeyAndLocale(key, "zh")`；<br>4) 若 DB 仍未命中（例如某条记录被误删或纯切片测试未挂 DB），查 `I18nDefaultCatalog` 内置种子兜底（同样先 `normalizedLocale` 再 `'zh'`），保证已登记 `error_code` 永远返回友好短句；<br>5) 若 `key` 连内置种子都不存在（未知 key），最终返回 `key` 本身（若 `key` 为空则返回 `ErrorCodes.PROVIDER_UNAVAILABLE`），**绝不返回 `null`、空串或异常堆栈** |
| 安全占位符插值 | 模板支持 `{placeholder}` 形式的具名占位符（仅匹配 `\{([a-zA-Z0-9_]+)\}`）：当传入 `params` 包含对应 key 且值非 null 时替换为 `String.valueOf(val)`（并截断单个参数值不超过 64 字符、剔除换行符以防注入）；未提供对应参数时保留原占位符或安全忽略，不抛异常 |
| 全局未捕获异常兜底 | 在异常处理器中新增 `@ExceptionHandler(Exception.class)` 兜底：记录服务端 `WARN`/`ERROR` 日志，但对 C 端固定返回 HTTP 200 + `ApiResponse.error(ErrorCodes.PROVIDER_UNAVAILABLE, i18n.resolve(ErrorCodes.PROVIDER_UNAVAILABLE, locale))`，彻底杜绝 SQL 错误、空指针或框架异常原文泄漏给浏览器 |

## 数据库变更：`V5__create_i18n_message.sql`

新建 `api/src/main/resources/db/migration/V5__create_i18n_message.sql`（**严禁改动 V1 / V2 / V3 / V4**）：

```sql
-- WP9: i18n_message table and stage-1 seed templates
-- Ref: docs/engineering/data-model/errors.md, docs/engineering/i18n-and-errors.md
CREATE TABLE i18n_message (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    key        TEXT        NOT NULL,
    locale     TEXT        NOT NULL CHECK (locale IN ('zh', 'en')),
    template   TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT i18n_message_key_locale_key UNIQUE (key, locale)
);
INSERT INTO i18n_message (key, locale, template) VALUES
    ('WALLET_INSUFFICIENT',  'zh', '账户余额不足，请先充值。'),
    ('WALLET_INSUFFICIENT',  'en', 'Insufficient wallet balance. Please top up first.'),
    ('JOB_NOT_FOUND',        'zh', '任务不存在。'),
    ('JOB_NOT_FOUND',        'en', 'The task does not exist.'),
    ('JOB_CANCELLED',        'zh', '任务已取消。'),
    ('JOB_CANCELLED',        'en', 'The task has been cancelled.'),
    ('JOB_FAILED',           'zh', '任务执行失败。'),
    ('JOB_FAILED',           'en', 'The task failed to run.'),
    ('PROVIDER_UNAVAILABLE', 'zh', '生成服务暂时不可用，请稍后重试。'),
    ('PROVIDER_UNAVAILABLE', 'en', 'The generation service is temporarily unavailable. Please try again later.'),
    ('IDEMPOTENCY_CONFLICT', 'zh', '同一个幂等键对应了不同的请求内容。'),
    ('IDEMPOTENCY_CONFLICT', 'en', 'The same idempotency key was used with a different request.'),
    ('UNAUTHORIZED',         'zh', '请先登录后再试。'),
    ('UNAUTHORIZED',         'en', 'Please sign in first.'),
    ('VALIDATION_ERROR',     'zh', '请求参数不合法。'),
    ('VALIDATION_ERROR',     'en', 'The request parameters are invalid.'),
    ('JOB_FAILED.RETRY',     'zh', '生成服务异常，正在重试（第 {attempt}/{max_attempts} 次）。'),
    ('JOB_FAILED.RETRY',     'en', 'Generation issue, retrying ({attempt}/{max_attempts}).')
ON CONFLICT (key, locale) DO NOTHING;
```

## 模块与包设计

### 1. api 新增 `art.pilipala.api.i18n` 包

建议结构（保持与 auth、job、billing 相同的分层风格）：

`art.pilipala.api.i18n.domain.I18nMessage`：

```java
record I18nMessage(UUID id, String key, String locale, String template, Instant updatedAt)
```

`art.pilipala.api.i18n.infra.I18nMessageRepository`：

```java
Optional<I18nMessage> findByKeyAndLocale(String key, String locale)
List<I18nMessage> findByKey(String key)
I18nMessage upsert(String key, String locale, String template) // 使用 INSERT INTO i18n_message (key, locale, template, updated_at) VALUES (?, ?, ?, now()) ON CONFLICT (key, locale) DO UPDATE SET template = EXCLUDED.template, updated_at = now() RETURNING ...，供集成测试与后续 WP11 Admin 复用
```

`art.pilipala.api.i18n.infra.JdbcI18nMessageRepository`：

```java
@Repository，使用 JdbcTemplate 实现上述接口；所有 SQL 只写在本类内
```

`art.pilipala.api.i18n.application.I18nDefaultCatalog`：

```java
纯函数静态工具类，持有 localeOf(String acceptLanguage) 与上述 9 个 key（8 个 ErrorCodes + JOB_FAILED.RETRY）的内置只读种子映射及 {placeholder} 安全插值函数 interpolate(String template, Map<String, Object> params)
```

`art.pilipala.api.i18n.application.I18nMessageService`：

```java
@Service，注入 I18nMessageRepository
public String localeOf(String acceptLanguage)：委托 I18nDefaultCatalog.localeOf(acceptLanguage)
public String resolve(String key, String locale)：resolve(key, locale, Map.of())
public String resolve(String key, String locale, Map<String, Object> params)：
    若 key == null || key.isBlank()，直接返回 ""；
    规范化 String lang = localeOf(locale)（'zh' 或 'en'）；
    查 repository.findByKeyAndLocale(key, lang)；
    若未命中且 "en".equals(lang)，查 repository.findByKeyAndLocale(key, "zh")；
    若 DB 命中，则对 msg.template() 执行 I18nDefaultCatalog.interpolate(template, params) 并返回；
    若 DB 未命中（或 DB 访问抛异常时捕获并记录 WARN 日志，绝不让查词条失败炸穿主流程），回退到 I18nDefaultCatalog.resolveDefault(key, lang, params)（内置种子同样遵循 lang -> 'zh' -> key 本身）。
```

`art.pilipala.api.i18n.application.RequestLocaleResolver`：

```java
@Component，注入 AuthSessionService 与 UserRepository
public String resolve(HttpServletRequest request) / public String resolve(UUID userId, String acceptLanguage)：
    若有已登录用户 userId（或 sessions.isLoggedIn() 为 true 且能解析出 UUID），查 users.findById(userId)；若用户存在且 user.locale() 为 "zh" 或 "en"，直接返回 user.locale()；
    否则返回 I18nDefaultCatalog.localeOf(acceptLanguage)（从 Accept-Language 解析 'en' 或 'zh'）。
    查用户过程若出现任何异常，吞掉并回退到 I18nDefaultCatalog.localeOf(acceptLanguage)。
```

### 2. 替换并删除 `AuthErrorMessages` 与 `JobErrorMessages`

删除文件：

```text
api/src/main/java/com/pilipala/api/auth/infra/AuthErrorMessages.java
api/src/main/java/com/pilipala/api/job/infra/JobErrorMessages.java
api/src/test/java/com/pilipala/api/job/infra/JobErrorMessagesTest.java（替换为 art.pilipala.api.i18n.I18nMessageServiceTest）
```

更新 GetJob、GetJobResult、CancelJob、JobView：

- `JobView`：将静态工厂改为 `public static JobView of(Job job, String message)`（直接接收已由 `I18nMessageService` 解析好的 message，不再在 DTO 内部调静态类）。
- `GetJob`：构造函数注入 `I18nMessageService`；当 `job.errorCode() != null` 时调用 `i18n.resolve(job.errorCode(), locale)` 生成 message 传给 `JobView.of(job, message)`。
- `GetJobResult`：构造函数注入 `I18nMessageService`；当 errorCode != null 时调用 `i18n.resolve(errorCode, locale)`。
- `CancelJob`：构造函数注入 `I18nMessageService`；返回 `JobView.of(job, job.errorCode() == null ? null : i18n.resolve(job.errorCode(), locale))`。

更新 `JobController`：

- 注入 `RequestLocaleResolver`（或 `ObjectProvider<RequestLocaleResolver>`，兼容 `@WebMvcTest` 窄切片：当窄切片未注入 `RequestLocaleResolver` 时回退 `I18nDefaultCatalog.localeOf(acceptLanguage)`）；
- 在 get、result、cancel 中通过 `resolveLocale(userId, acceptLanguage)` 计算有效 locale（从而让设置了 `users.locale='en'` 的登录用户即使不传 `Accept-Language: en` 也能拿到英文任务错误提示）。

更新 `AuthExceptionHandler` 与 `JobExceptionHandler`：

- 注入 `ObjectProvider<I18nMessageService>` 与 `ObjectProvider<RequestLocaleResolver>`（在 Spring Boot 完整容器下走数据库 `I18nMessageService` + 用户偏好 `RequestLocaleResolver`；在未导入 i18n Bean 的 `@WebMvcTest` 切片下自动回退 `I18nDefaultCatalog`，这样现有 `AuthController` / `JobController` / `WalletController` 切片测试无需繁琐改写即可平滑工作，同时也可在测试中显式注入/Mock `I18nMessageService` 验证 DB 动态改词条！）。
- 在 `AuthExceptionHandler` 中新增兜底方法：

```java
@ExceptionHandler(Exception.class)
@ResponseStatus(HttpStatus.OK)
public ApiResponse<Void> onUnexpected(Exception e, HttpServletRequest request) {
    log.error("Unhandled exception on {}", request.getRequestURI(), e);
    return respond(ErrorCodes.PROVIDER_UNAVAILABLE, request);
}
```

确保任何未预期的数据库异常、运行时异常都不会把堆栈或 `e.getMessage()` 暴露给 C 端。

## 文档同步要求

- 新建 `docs/engineering/feature-briefs/2026-10-24-wp9-friendly-errors-i18n-message.md`（本文件全文原样落仓）。
- 更新 `docs/engineering/feature-briefs/README.md`：
  - 将 WP8 状态从 已实现（待验收） 更新为 已实现；
  - 新增 WP9 行：`| [2026-10-24-wp9-friendly-errors-i18n-message.md](./2026-10-24-wp9-friendly-errors-i18n-message.md) | WP9 友好错误与 i18n（i18n_message 表） | WP9 / M2 | 已实现（待验收） |`。
- 更新 `docs/engineering/data-model/README.md`：
  - 将 `i18n_message` 行的落地 WP 状态更新为 WP9（已落地，V5）。
- 更新 `docs/engineering/runbooks/local-dev.md`：
  - 增加 WP9 验证章节：演示通过 psql 修改 `i18n_message` 表中某条词条（例如将 `WALLET_INSUFFICIENT` 的 `'zh'` 模板临时改为自定义文案），无需重启 api，直接 curl 触发余额不足或查询失败任务即可实时看到数据库新文案生效；以及验证 `users.locale='en'` 用户偏好优先级与缺词条回退。

## 验收清单（PR Review 逐条对照）

- [ ] `docs/engineering/feature-briefs/2026-10-24-wp9-friendly-errors-i18n-message.md` 已落仓，且 `feature-briefs/README.md`、`data-model/README.md`、`runbooks/local-dev.md` 已同步更新
- [ ] `V5__create_i18n_message.sql` 表名、5 个列名（id、key、locale、template、updated_at）、`CHECK (locale IN ('zh', 'en'))`、`UNIQUE (key, locale)` 与 `data-model/errors.md` 逐字一致，且灌入全部 8 个已登记 error_code + `JOB_FAILED.RETRY` 的中英文种子（共 18 行）
- [ ] V1 / V2 / V3 / V4 迁移文件 0 行改动
- [ ] 临时类 `AuthErrorMessages.java` 与 `JobErrorMessages.java` 已删除，全仓检索 `AuthErrorMessages|JobErrorMessages` 命中数为 0
- [ ] `I18nMessageService` 实现完整回退链：DB (key, locale) → DB (key, 'zh') → 内置默认种子 → key 本身；当数据库中动态 `UPDATE i18n_message SET template = ...` 后，C 端接口无需重启即可返回更新后的文案
- [ ] 支持 `{placeholder}` 安全插值（如 `JOB_FAILED.RETRY` 传入 attempt=2, max_attempts=3 解析为 "生成服务异常，正在重试（第 2/3 次）。"），缺参不抛异常、超长或带换行符的参数值被安全净化
- [ ] 语言选择优先级符合 `i18n-and-errors.md`：已登录用户若设置 `users.locale='en'`，即使请求未带 `Accept-Language: en`，错误响应与任务查询也优先返回英文文案；未设置 `users.locale`（为 null）或未登录时，按 `Accept-Language`（`en*` → `'en'`，否则 `'zh'`）解析
- [ ] 全局未捕获 Exception 由 `@ExceptionHandler(Exception.class)` 兜底为 HTTP 200 + `error_code="PROVIDER_UNAVAILABLE"` + 本地化友好短句，绝不向 C 端输出 `e.getMessage()`、SQL 表名或堆栈
- [ ] `worker/`、`web/`、`docs/content/**`、`CHANGELOG.md` 0 行改动
- [ ] `./mvnw test` 全部单测通过（含新增的 `I18nMessageServiceTest`、`RequestLocaleResolverTest`、未捕获异常脱敏测试，以及更新后的 `JobLifecycleTest` 等）
- [ ] 分支 `feat/wp9-friendly-errors-i18n-message`；PR base = main；无真实密钥入库；不要自行 merge

## 非目标

- 不把前端静态 UI 词条（按钮、导航栏、设置弹窗）塞进 `i18n_message` 表
- 不做运营改词条的 Admin HTTP 接口或后台页面（留给 WP11）
- 不引入 Redis 词条缓存或复杂的缓存失效广播（stage-1 词条表仅数十行，按主键/唯一索引 `(key, locale)` 直查 Postgres 耗时 < 1ms，且天然支持运维改表即刻生效）
- 不改 worker 模块与 web 前端

## 给 Minimax 的额外约束

- 先读再写：`DEVELOPMENT.md`（WP9）、ADR 0004 / 0005、`data-model/errors.md`、`i18n-and-errors.md`、`api/gateway-contract.md`、现有 `ErrorCodes.java`、`AuthErrorMessages.java`、`JobErrorMessages.java`、`AuthExceptionHandler.java`、`JobExceptionHandler.java`、`GetJob.java`、`GetJobResult.java`、`CancelJob.java`、`JobView.java`。
- 注意现有单测兼容：
  - `JobLifecycleTest` 在构造 `GetJob`、`GetJobResult`、`CancelJob` 时需传入 `I18nMessageService`（单测可用基于内存 Map 的 `InMemoryI18nMessageRepository` 构造 `I18nMessageService`）。
  - 在 `JobLifecycleTest` 或 `I18nMessageServiceTest` 中补充测试：修改 `InMemoryI18nMessageRepository` 中的模板后，`GetJob` / `GetJobResult` 返回的 message 随之动态变化。
- 全部测试必须绿：`./mvnw test` 同时跑通 api 与 worker 全部单测。
- 做完用简短中文汇报：改了哪些模块、V5 建表与种子情况、本地双进程 + psql 改表 + curl 实测结果（验证 `Accept-Language: en`、`users.locale='en'` 优先级、`UPDATE i18n_message` 实时生效、缺词条回退、模拟上游失败不漏原文）、故意没做哪些（静态 UI 词条入库 / Admin 界面 / 前端改动 / worker 改动）。

## 留给实现者的选择（最多拍这 1 项，PR 写明选了哪样）

内存测试仓储 `InMemoryI18nMessageRepository` 的放置位置：

- **方式 A（推荐）**：放在 `api/src/test/java/com/pilipala/api/i18n/InMemoryI18nMessageRepository.java`（与 `InMemoryJobStore` / `InMemoryWalletStore` 风格一致，生产包只留 `I18nMessageRepository` 接口与 `JdbcI18nMessageRepository` 实现）；
- **方式 B**：在 `I18nMessageService` 中允许通过静态工厂 `I18nMessageService.withDefaultsOnly()` 快速构造仅供单测使用的实例。

建议优先选方式 A，既能测真实仓储读写与动态覆盖，又保持生产包纯净。

不要再自行扩大其它开放问题。

## 分支 / Commit / PR 指令

```bash
git fetch origin
git checkout main
git pull origin main
git checkout -b feat/wp9-friendly-errors-i18n-message
git add docs/engineering api
git commit -m "WP9: i18n_message 表与友好错误本地化闭环"
git push -u origin feat/wp9-friendly-errors-i18n-message
```

开 PR → `main`：

- 标题：`WP9: 友好错误与 i18n_message 表`
- 正文必须含：brief 路径 `docs/engineering/feature-briefs/2026-10-24-wp9-friendly-errors-i18n-message.md`、做了什么、验收自勾、故意没做（无静态 UI 入库 / 无 Admin CRUD / worker 与 web 零改动）、本地 psql + curl 实测记录、实现者选择结论
- **不要自行 merge。**
