# Feature brief：WP7 前端生成改打 pilipala API（登录 + 钱包 + `submit/query/result/cancel`）

- 日期：2026-10-24
- 作者：pilipala engineering
- 对应 WP / 里程碑：WP7 / M2（前端生图接入 pilipala API 完整闭环）
- 状态：已确认（待实现）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax（上方面包屑硬约束一并保留）。不要摘要。

本任务落地 **pilipala M2 收官包 WP7（前端生成改打 pilipala API）**：在 `web/` 前端新增对接 pilipala 后端网关的统一客户端（`pilipala-client.ts`、`pilipala-auth.ts`、`pilipala-jobs.ts`）、登录态与钱包状态仓（`use-auth-store.ts`，持久化 `pilipala-token`）、登录/注册弹窗（`auth-modal.tsx`）以及顶栏账户与余额徽章；将默认渠道模式切为托管云端模式（`channelMode: "remote"`），使画布（`/canvas/:projectId`）与图片工作台（`/image`）在不配置任何浏览器本地供应商 API Key 的前提下，直接走 `POST /api/v1/jobs/submit`（请求体含 `idempotency_key`、`capability: "image.generate"`、`model`、`params`）→ 轮询 `GET /api/v1/jobs/{jobId}` → 拉取 `GET /api/v1/jobs/{jobId}/result`（展示 WP8 火山 TOS 短期预签名读 URL `outputs[].url`）→ 取消时调 `POST /api/v1/jobs/{jobId}/cancel`，并直接展示 WP9 `i18n_message` 返回的本地化友好错误文案。**严守 DEVELOPMENT.md 与 ADR 0001 / 0003 / 0004 / 0005：C 端托管路径不在浏览器保存供应商 API Key、不直连上游模型供应商、`api/` 与 `worker/` 后端代码及 `V1`–`V5` 迁移零改动。**

## 背景

在按「先做完 M1–M2 后端再接前端」的顺序推进后，WP0–WP6、WP8、WP9 已全部合入 `main`（当前最新为 WP9 `03c2c23`）：

- **WP3（`V1`）**：`AuthController` 已提供 `POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`POST /api/v1/auth/logout`、`GET /api/v1/auth/me`，Sa-Token 会话通过请求头 `pilipala-token` 传递；
- **WP4（`V2`）+ WP5（`V3`）**：`JobController` 已提供 `POST /api/v1/jobs/submit`、`GET /api/v1/jobs/{jobId}`、`GET /api/v1/jobs/{jobId}/result`、`POST /api/v1/jobs/{jobId}/cancel`，由 `worker` 消费 RabbitMQ 调唯一 `AnytokenGatewayAdapter`；
- **WP6（`V4`）**：`WalletController` 已提供 `GET /api/v1/wallet`（返回 `user_id`、`available`、`frozen`、`updated_at`），并完成任务提交 `freeze`、成功 `confirm`、失败/取消 `release` 预付费账本闭环；
- **WP8**：`GET /api/v1/jobs/{jobId}/result` 在 `status == "succeeded"` 时已为每个 `object_key` 签发短期有效（默认 900s）的 `TOS4-HMAC-SHA256` 预签名读 URL（`outputs[].url`）；
- **WP9（`V5`）**：全部失败响应与任务状态查询已按「`users.locale` → `Accept-Language` → `'zh'`」从 `i18n_message` 表返回本地化友好短句（含重试动态插值 `JOB_FAILED.RETRY`）。

但当前 `web/` 前端仍停留在开源模板的纯本地直连状态：

1. `web/src/stores/use-config-store.ts` 的 `useEffectiveConfig()` 硬编码返回 `channelMode: "local"`，强制要求用户在浏览器里填写供应商 `baseUrl` 和 `apiKey` 才能点生成；
2. `web/src/services/api/image.ts` 只支持浏览器直连第三方 `/v1/images/generations` 或 `/v1/chat/completions`，尚未接入 pilipala `/api/v1/jobs/submit`；
3. 前端尚无 `/api/v1/auth/*` 登录/注册界面与 `/api/v1/wallet` 余额展示。

完成本 WP 后，**里程碑 M2（真实生图 + 预付费扣费 + TOS 签名读地址 + 友好错误）前后端全链路正式合龙**。

[DEVELOPMENT.md](../DEVELOPMENT.md) WP7：

> - **里程碑**：M2
> - **做什么**：托管模式下，画布生图改为 `submit / query / result` 内部合同；浏览器不再保存供应商 Key 作为正式路径。
> - **不做**：删掉所有本地开源直连代码（若暂留，必须明确是非托管调试路径，且不走钱包）。
> - **完成**：画布至少一条官方生图路径只打 pilipala API。

## 必须遵守

- **ARCHITECTURE.md & ADR 0001**：C 端浏览器只认识 pilipala API（/api/v1/*），不认识 Anytoken 或上游模型供应商，托管模式（channelMode: "remote"）下不在浏览器端存储或发送任何模型供应商 API Key
- **ADR 0003**：生图统一走异步任务契约：POST /api/v1/jobs/submit → 轮询 GET /api/v1/jobs/{jobId} → GET /api/v1/jobs/{jobId}/result；用户点击停止/取消时调 POST /api/v1/jobs/{jobId}/cancel；请求 params 只传已物化参数（prompt、size、quality 等），严禁把整张画布文档或参考图 Base64 大字节塞进 params
- **ADR 0005 & i18n-and-errors.md**：
  - 两层 i18n 严格分工：
    - 前端静态 UI 文案（登录弹窗标题、输入框标签、顶栏余额标签、托管/本地模式切换说明）写在 web/src/i18n/locales/{zh-CN,en-US}.ts；
    - 后端业务错误与任务状态提示（如 WALLET_INSUFFICIENT、UNAUTHORIZED、PROVIDER_UNAVAILABLE、重试中 JOB_FAILED.RETRY）直接展示后端返回的 message 字段，前端请求固定携带 Accept-Language: <当前语言>（zh-CN 或 en-US）
- **api/gateway-contract.md 与现有后端 Controller 契约**：
  - 所有 /api/v1/* 响应遵循统一信封 {"ok": boolean, "data": ..., "error_code": string | null, "message": string | null}（HTTP 状态码恒为 200，业务成功/失败看 ok 与 error_code）
  - 认证令牌走请求头 pilipala-token: <token>（与 api/src/main/resources/application.yml 中 sa-token.token-name: pilipala-token、is-read-header: true 一致）
- **CONTRIBUTING.md**：
  - 本 WP 只改 web/ 与 docs/engineering/；
  - api/、worker/、Flyway V1–V5、docs/content/**、CHANGELOG.md 必须 0 行改动

## 本 WP 拍板（已决定，禁止再猜）

| 项 | 锁定值 |
| --- | --- |
| 后端代码与迁移 | api/ 与 worker/ 0 行改动，无新增 Flyway 迁移 |
| 开发环境代理与 API 根路径 | 在 web/vite.config.ts 新增 server.proxy["/api"] 转发到 process.env.VITE_API_PROXY_TARGET \|\| "http://localhost:8080"（changeOrigin: true）；web/src/constant/env.ts 新增 API_BASE_URL = (import.meta.env.VITE_API_BASE_URL \|\| "").replace(/\/+$/, "")（默认为空串即走同源 /api/v1/...） |
| 会话 Token 存储与传递 | 登录/注册成功后返回的 token 存入 localStorage（键名 PILIPALA_AUTH_TOKEN_KEY = "pilipala:auth_token"（启动时从旧键 flora:auth_token 迁移））；所有 /api/v1/* 请求在 token 存在时自动附带请求头 pilipala-token: &lt;token&gt; |
| 默认渠道模式（channelMode） | defaultConfig.channelMode 设为 "remote"，且 useEffectiveConfig() 保留 `config.channelMode`（若无则默认 "remote"） |
| 云端内置可选生图模型 | 在 use-config-store.ts 定义导出常量 PILIPALA_REMOTE_IMAGE_MODELS = [{ name: "gpt-image-1", capability: "image" }, { name: "dall-e-3", capability: "image" }, { name: "flux-1-schnell", capability: "image" }]；当 config.channelMode === "remote" 时，selectableModelsByCapability(config, "image") 包含上述云端模型，无需配置本地渠道即可在画布和图片工作台直接选模型并点生成 |
| isAiConfigReady 兼容规则 | 1. 若 model 含渠道前缀（即 decodeChannelModel(model) !== null，形如 channelId::modelName），始终按原逻辑校验 findChannelModel(config, model) 的 baseUrl 与 apiKey 非空（确保现有 web/tests/model-selection.test.ts 8 个单测 100% 原样通过）；<br>2. 若 model 不含渠道前缀且 config.channelMode === "remote"，只要 PILIPALA_REMOTE_IMAGE_MODELS.some(m => m.name === model.trim()) 即返回 true |
| 轮询参数与取消行为 | runPilipalaImageJob 默认轮询间隔 800ms（支持测试注入 pollIntervalMs: 10），单次任务最长等待超时 120_000ms；当传入的 AbortSignal 触发 abort 时，立即异步调用 POST /api/v1/jobs/{jobId}/cancel 并刷新钱包余额，随后抛出 new Error(i18n.t("common.requestCanceled"))（与现有画布 isGenerationCanceled 完美兼容） |
| 未登录与余额不足交互 | 1. 当 /api/v1/jobs/submit 返回 error_code === "UNAUTHORIZED"（或本地无 token）时，自动打开登录/注册弹窗（useAuthStore.getState().setAuthModalOpen(true, "login")），并抛出带后端本地化 message 的 PilipalaApiError；<br>2. 当返回 error_code === "WALLET_INSUFFICIENT" 时，立即刷新顶栏钱包余额（refreshWallet()），并抛出带后端本地化 message 的 PilipalaApiError |
| 钱包余额联动刷新时机 | 在应用启动（initialize）、登录/注册成功、任务提交成功（看到 available 扣减、frozen 增加）、任务进入终态（succeeded / cancelled / dead，看到 frozen 归零）时自动调用 refreshWallet() 更新顶栏余额徽章 |

### 后端接口与 DTO 精确对照表（与 main 上的 Java Controller 100% 对齐）

前端所有请求路径、请求体字段、响应 data 字段必须严格按以下契约编写（后端已开启 spring.jackson.property-naming-strategy: SNAKE_CASE）：

| 接口 | 方法与路径 | 请求头 | 请求体 JSON | 成功响应 data JSON |
| --- | --- | --- | --- | --- |
| 注册 | POST /api/v1/auth/register | Accept-Language | {"username": "alice_01", "password": "password123", "locale": "zh"}<br>（注：username 需满足 ^[a-zA-Z0-9_]{3,32}$，password ≥ 8 位，locale 为 "zh" 或 "en"） | {"user_id": "&lt;uuid&gt;", "username": "alice_01", "locale": "zh", "token": "&lt;sa-token&gt;"} |
| 登录 | POST /api/v1/auth/login | Accept-Language | {"username": "alice_01", "password": "password123"} | {"user_id": "&lt;uuid&gt;", "username": "alice_01", "locale": "zh", "token": "&lt;sa-token&gt;"} |
| 登出 | POST /api/v1/auth/logout | pilipala-token, Accept-Language | 无（或 {}） | {} |
| 当前用户 | GET /api/v1/auth/me | pilipala-token, Accept-Language | 无 | {"user_id": "&lt;uuid&gt;", "username": "alice_01", "locale": "zh"} |
| 查询钱包 | GET /api/v1/wallet | pilipala-token, Accept-Language | 无 | {"user_id": "&lt;uuid&gt;", "available": 90, "frozen": 10, "updated_at": "2026-10-24T..."} |
| 提交任务 | POST /api/v1/jobs/submit | pilipala-token, Accept-Language | {"idempotency_key": "&lt;string&gt;", "capability": "image.generate", "model": "gpt-image-1", "params": {"prompt": "...", "size": "...", "quality": "..."}, "client_job_id": "&lt;optional&gt;"} | {"job_id": "&lt;uuid&gt;", "status": "queued", "hold_id": "&lt;uuid&gt;"} |
| 查询状态 | GET /api/v1/jobs/{jobId} | pilipala-token, Accept-Language | 无 | {"job_id": "&lt;uuid&gt;", "status": "queued\|running\|succeeded\|cancelled\|dead", "attempt": 1, "max_attempts": 3, "error_code": null, "message": null, "updated_at": "..."} |
| 获取结果 | GET /api/v1/jobs/{jobId}/result | pilipala-token, Accept-Language | 无 | {"job_id": "&lt;uuid&gt;", "status": "succeeded", "outputs": [{"type": "image", "object_key": "anytoken/img/.../1.png", "url": "https://..."}], "error_code": null, "message": null} |
| 取消任务 | POST /api/v1/jobs/{jobId}/cancel | pilipala-token, Accept-Language | 无 | 同 GET /api/v1/jobs/{jobId} 的 JobView |

## 模块与文件设计

### 1. 环境常量与 Vite 代理（`web/src/constant/env.ts` & `web/vite.config.ts`）

`web/src/constant/env.ts`：

新增导出：

```ts
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "").replace(/\/+$/, "");
```

`web/vite.config.ts`：

在 `defineConfig` 中增加 `server.proxy`：

```ts
server: {
    proxy: {
        "/api": {
            target: process.env.VITE_API_PROXY_TARGET || "http://localhost:8080",
            changeOrigin: true,
        },
    },
},
```

### 2. pilipala API 统一信封客户端（`web/src/services/api/pilipala-client.ts`，新增）

常量与 Token 存取辅助函数：

```ts
export const PILIPALA_AUTH_TOKEN_KEY = "pilipala:auth_token"（启动时从旧键 flora:auth_token 迁移）;
export const PILIPALA_TOKEN_HEADER = "pilipala-token";
export function getStoredPilipalaToken(): string | null {
    try {
        return globalThis.localStorage?.getItem(PILIPALA_AUTH_TOKEN_KEY) || null;
    } catch {
        return null;
    }
}
export function setStoredPilipalaToken(token: string | null) {
    try {
        if (!token) globalThis.localStorage?.removeItem(PILIPALA_AUTH_TOKEN_KEY);
        else globalThis.localStorage?.setItem(PILIPALA_AUTH_TOKEN_KEY, token);
    } catch {
        // ignore storage errors
    }
}
```

类型定义：

```ts
export type PilipalaApiEnvelope<T> = {
    ok: boolean;
    data: T | null;
    error_code: string | null;
    message: string | null;
};
export class PilipalaApiError extends Error {
    readonly errorCode: string;
    constructor(errorCode: string, message: string) {
        super(message);
        this.name = "PilipalaApiError";
        this.errorCode = errorCode;
    }
}
```

请求函数 `pilipalaRequest<T>(path: string, options?: RequestInit & { token?: string | null; fetchImpl?: typeof fetch }): Promise<T>`：

- 拼接 URL：`${API_BASE_URL}${path.startsWith("/") ? path : `/${path}`}`。
- 自动设置请求头：
  - `Accept: "application/json"`
  - `Accept-Language: i18n.resolvedLanguage || i18n.language || "zh-CN"`
  - 若 `options?.token ?? getStoredPilipalaToken()` 非空，设置 `pilipala-token: <token>`
  - 若有 `options?.body` 且未显式传 Content-Type，设置 `Content-Type: "application/json"`
- 解析 JSON 信封 `PilipalaApiEnvelope<T>`：
  - 若 `envelope && typeof envelope.ok === "boolean"`：
    - `envelope.ok === true`：返回 `envelope.data as T`；
    - `envelope.ok === false`：抛出 `new PilipalaApiError(envelope.error_code || "PROVIDER_UNAVAILABLE", envelope.message || i18n.t("canvas.projectPage.generationFailed"))`。
  - 若响应非 JSON（例如网关未启动返回 502 HTML），抛出 `new PilipalaApiError("PROVIDER_UNAVAILABLE", i18n.t("canvas.projectPage.generationFailed"))`（绝不向界面吐出 HTML 原文）。

### 3. 认证与钱包 API 及状态仓（`web/src/services/api/pilipala-auth.ts` & `web/src/stores/use-auth-store.ts`，新增）

`web/src/services/api/pilipala-auth.ts`：

类型（严格对齐 `AuthSession`、`CurrentUser`、`WalletView`）：

```ts
export type PilipalaUser = {
    user_id: string;
    username: string;
    locale: string | null;
};
export type PilipalaAuthSession = PilipalaUser & {
    token: string;
};
export type PilipalaWallet = {
    user_id: string;
    available: number;
    frozen: number;
    updated_at: string;
};
```

函数（均支持可选注入 `fetchImpl` 便于单测）：

- `registerUser(input: { username: string; password: string; locale?: "zh" | "en" }, fetchImpl?)`：POST `/api/v1/auth/register`
- `loginUser(input: { username: string; password: string }, fetchImpl?)`：POST `/api/v1/auth/login`
- `logoutUser(fetchImpl?)`：POST `/api/v1/auth/logout`
- `fetchCurrentUser(fetchImpl?)`：GET `/api/v1/auth/me`
- `fetchWallet(fetchImpl?)`：GET `/api/v1/wallet`

`web/src/stores/use-auth-store.ts`：

使用 Zustand 管理：

- `token: string | null`（初始值读取 `getStoredPilipalaToken()`）
- `user: PilipalaUser | null`
- `wallet: PilipalaWallet | null`
- `loading: boolean`
- `initialized: boolean`
- `authModalOpen: boolean`
- `authModalTab: "login" | "register"`

方法：

- `initialize(fetchImpl?)`：若本地无 `getStoredPilipalaToken()`，直接置 `user: null, wallet: null, initialized: true`；若有 token，调 `fetchCurrentUser(fetchImpl)` 与 `fetchWallet(fetchImpl)` 更新 user 和 wallet；若返回 UNAUTHORIZED，清除失效 token（`setStoredPilipalaToken(null)`）并置 `user: null, wallet: null, initialized: true`。
- `refreshWallet(fetchImpl?)`：若当前有 token，调 `fetchWallet(fetchImpl)` 更新 wallet（失败时静默忽略）。
- `login(input, fetchImpl?)`：调 `loginUser(input, fetchImpl)` → 保存 `setStoredPilipalaToken(session.token)` → 更新 token 与 user → 调 `await refreshWallet(fetchImpl)` → 关闭 `authModalOpen: false`。
- `register(input, fetchImpl?)`：调 `registerUser(input, fetchImpl)` → 保存 `setStoredPilipalaToken(session.token)` → 更新 token 与 user → 调 `await refreshWallet(fetchImpl)` → 关闭 `authModalOpen: false`。
- `logout(fetchImpl?)`：调 `logoutUser(fetchImpl)`（忽略异常）→ `setStoredPilipalaToken(null)` → 清空 `token: null, user: null, wallet: null`。
- `setAuthModalOpen(open: boolean, tab?: "login" | "register")`。

### 4. 异步生图任务客户端（`web/src/services/api/pilipala-jobs.ts`，新增）

类型定义（严格对齐 `SubmitJobView`、`JobView`、`JobResultView`）：

```ts
export type PilipalaJobStatus = "created" | "queued" | "running" | "succeeded" | "cancelled" | "dead";
export type PilipalaSubmitJobRequest = {
    idempotency_key: string;
    capability: string;
    model: string;
    params: Record<string, unknown>;
    client_job_id?: string;
};
export type PilipalaSubmitJobView = {
    job_id: string;
    status: PilipalaJobStatus;
    hold_id: string | null;
};
export type PilipalaJobView = {
    job_id: string;
    status: PilipalaJobStatus;
    attempt: number;
    max_attempts: number;
    error_code: string | null;
    message: string | null;
    updated_at: string;
};
export type PilipalaJobOutput = {
    type: string;
    object_key: string;
    url: string | null;
};
export type PilipalaJobResultView = {
    job_id: string;
    status: PilipalaJobStatus;
    outputs: PilipalaJobOutput[];
    error_code: string | null;
    message: string | null;
};
```

核心函数：

- `submitPilipalaJob(payload: PilipalaSubmitJobRequest, options?: { signal?: AbortSignal; fetchImpl?: typeof fetch }): Promise<PilipalaSubmitJobView>` —— POST `/api/v1/jobs/submit`，Body 为 JSON payload。
- `getPilipalaJob(jobId: string, options?: { signal?: AbortSignal; fetchImpl?: typeof fetch }): Promise<PilipalaJobView>` —— GET `/api/v1/jobs/${encodeURIComponent(jobId)}`
- `getPilipalaJobResult(jobId: string, options?: { signal?: AbortSignal; fetchImpl?: typeof fetch }): Promise<PilipalaJobResultView>` —— GET `/api/v1/jobs/${encodeURIComponent(jobId)}/result`
- `cancelPilipalaJob(jobId: string, options?: { fetchImpl?: typeof fetch }): Promise<PilipalaJobView>` —— POST `/api/v1/jobs/${encodeURIComponent(jobId)}/cancel`

`runPilipalaImageJob(config: AiConfig, prompt: string, references?: ReferenceImage[], options?: { signal?: AbortSignal; pollIntervalMs?: number; timeoutMs?: number; idempotencyKey?: string; clientJobId?: string; fetchImpl?: typeof fetch; onStatusChange?: (job: PilipalaJobView) => void }): Promise<Array<GeneratedImageItem & { id: string }>>`：

- 解析模型名：`const model = modelOptionName(config.model || config.imageModel || "gpt-image-1") || "gpt-image-1"`。
- 组装请求参数 `params`（必须含 prompt）：

```ts
const params: Record<string, unknown> = {
    prompt: prompt.trim(),
    size: config.size || "auto",
    quality: config.quality || "auto",
};
if (config.background) params.background = config.background;
if (references?.length) params.reference_count = references.length;
```

（注意：严禁往 params 塞整张画布或参考图 Base64 字节，符合 gateway-contract.md 与 ADR 0003）。

- 调 `submitPilipalaJob({ idempotency_key: options?.idempotencyKey || nanoid(), capability: "image.generate", model, params, ...(options?.clientJobId ? { client_job_id: options.clientJobId } : {}) }, { signal: options?.signal, fetchImpl: options?.fetchImpl })`：
  - 若捕获 `PilipalaApiError`：
    - 当 `err.errorCode === "UNAUTHORIZED"`：调 `useAuthStore.getState().setAuthModalOpen(true, "login")`；
    - 当 `err.errorCode === "WALLET_INSUFFICIENT"`：调 `void useAuthStore.getState().refreshWallet(options?.fetchImpl)`；
    - 原样重新抛出 `err`（上层 ImagePage 与 CanvasProjectPage 会用 `err.message` 弹出 toast 并写入节点 errorDetails）。
- 提交成功后拿到 `jobId = submitted.job_id`，立即触发 `void useAuthStore.getState().refreshWallet(options?.fetchImpl)`。
- 绑定取消监听：若 `options?.signal` 在轮询期间触发 abort，立即异步调用 `cancelPilipalaJob(jobId, { fetchImpl: options?.fetchImpl }).catch(() => undefined).finally(() => void useAuthStore.getState().refreshWallet(options?.fetchImpl))`，并抛出 `new Error(i18n.t("common.requestCanceled"))`。
- 循环轮询 `getPilipalaJob(jobId)`（间隔 `options?.pollIntervalMs ?? 800` ms，总超时 `options?.timeoutMs ?? 120_000` ms）：
  - 每次拿到 jobView 调用 `options?.onStatusChange?.(jobView)`；
  - 若 `jobView.status === "succeeded"`：跳出轮询；
  - 若 `jobView.status === "cancelled"`：调 `void useAuthStore.getState().refreshWallet(options?.fetchImpl)`，抛出 `new Error(jobView.message || i18n.t("common.requestCanceled"))`；
  - 若 `jobView.status === "dead"`：调 `void useAuthStore.getState().refreshWallet(options?.fetchImpl)`，抛出 `new PilipalaApiError(jobView.error_code || "JOB_FAILED", jobView.message || i18n.t("canvas.projectPage.generationFailed"))`；
  - 若仍为 `"created" | "queued" | "running"`：等待下一个 pollIntervalMs。
- 调 `getPilipalaJobResult(jobId)` 并刷新钱包 `void useAuthStore.getState().refreshWallet(options?.fetchImpl)`：
  - 校验 `resultView.outputs` 非空且含非空 url；
  - 对每个含 url 的 output 获取图片（尝试通过 fetchImpl 拉取转为 dataUrl；若跨域或环境无 FileReader 则回退为 `{ dataUrl: output.url, mimeType: "image/png", bytes: 0 }`），返回 `Array<GeneratedImageItem & { id: string }>`。

### 5. 改造 `use-config-store.ts` 与 `services/api/image.ts`

`web/src/stores/use-config-store.ts`：

新增导出：

```ts
export const PILIPALA_REMOTE_IMAGE_MODELS: ChannelModel[] = [
    { name: "gpt-image-1", capability: "image" },
    { name: "dall-e-3", capability: "image" },
    { name: "flux-1-schnell", capability: "image" },
];
```

`defaultConfig.channelMode` 改为 `"remote"`；持久化 merge 中保留用户已保存的 channelMode（若无则默认 `"remote"`）。

`useEffectiveConfig()` 改为：

```ts
export function useEffectiveConfig() {
    const config = useConfigStore((state) => state.config);
    return useMemo(() => ({ ...config, channelMode: config.channelMode || ("remote" as const) }), [config]);
}
```

`selectableModelsByCapability(config, capability)`：

- 当 `config.channelMode === "remote"` 且 `(!capability || capability === "image")` 时，将 `PILIPALA_REMOTE_IMAGE_MODELS.map((m) => m.name)` 合并在返回列表最前面（去重），使模型选择器默认即可选 gpt-image-1、dall-e-3、flux-1-schnell。

`resolveModelForCapability(config, preferred, capability)`：

- 保持对显式传入 channelId::modelName 的全部既有语义（若 `decodeChannelModel(preferred)?.channelId` 在 config.channels 中不存在，或该模型已从该渠道移除，原样返回 preferred，绝不替换成默认模型）；
- 当 `config.channelMode === "remote"` 且 capability === "image" 时，若 preferred 是 PILIPALA_REMOTE_IMAGE_MODELS 中的模型名则直接返回 preferred；若未传 preferred，优先返回匹配的 config.imageModel 或 "gpt-image-1"。

`isAiConfigReady(config, model)`：

- 若 `decodeChannelModel(model)` 非空（带 channelId:: 前缀），严格走原逻辑 `Boolean(match && match.channel.baseUrl.trim() && match.channel.apiKey.trim())`；
- 否则若 `config.channelMode === "remote"` 且 `PILIPALA_REMOTE_IMAGE_MODELS.some((item) => item.name === model.trim())`，返回 true；
- 其余情况走原逻辑。

`web/src/services/api/image.ts`：

- 在 `requestGeneration(config, prompt, options)` 与 `requestEdit(config, prompt, references, options)` 开头判断：
  - 若 `config.channelMode === "remote" && !decodeChannelModel(config.model)`（即使用 pilipala 托管云端模式且未显式指定本地渠道模型），直接调用并返回 `runPilipalaImageJob(config, prompt, references, options)`！
  - 否则（channelMode === "local" 或显式选择了 channelId::modelName 本地调试渠道），保留原有本地直连逻辑。

### 6. 登录/注册弹窗、顶栏账户与余额展示、配置面板开发开关

新增 `web/src/components/auth/auth-modal.tsx`：

- 使用 Ant Design Modal + Tabs（登录 / 注册）+ Form + Input：
  - 登录页：username（用户名，3–32 位字母/数字/下划线）、password（密码）；
  - 注册页：username（3–32 位 `[a-zA-Z0-9_]`）、password（至少 8 位）；注册时自动传 `locale: i18n.resolvedLanguage === "en-US" ? "en" : "zh"`；
  - 提交时调 useAuthStore 的 login / register；若抛 `PilipalaApiError`，直接展示后端返回的本地化友好提示 `err.message`。

更新 `web/src/components/layout/user-status-actions.tsx`：

- 组件挂载时执行一次 `void useAuthStore.getState().initialize()`。
- 在顶栏右侧工具栏（同时出现在全局顶栏与画布右上角）：
  - 已登录状态：
    - 显示钱包积分徽章（如 可用 90 / 冻结 10，点击徽章可触发 `refreshWallet()` 刷新）；
    - 显示当前用户名 `user.username` 与「退出」按钮。
  - 未登录状态：
    - 显示「登录 / 注册」按钮，点击调 `setAuthModalOpen(true, "login")`。

在 `web/src/components/layout/app-top-nav.tsx` 挂载 `<AuthModal />`（注意：画布路由 /canvas/:id 隐藏了 `<header>`，但 `<AppTopNav />` 仍渲染弹窗片段 `<>...</>`，因此把 `<AuthModal />` 放在 `<AppConfigModal />` 旁边即可在全站含画布内生效）。

更新 `web/src/components/layout/app-config-modal.tsx`：

- 在「模型渠道（channels）」Tab 顶部增加 Radio.Group 切换 `config.channelMode`：
  - `"remote"`：pilipala 托管云端（默认推荐） —— 通过 pilipala 后端网关（/api/v1/jobs/submit）与预付费钱包生成图片，无需在浏览器配置供应商 API Key；
  - `"local"`：本地直连调试（非托管 / 不走钱包） —— 仅当切换到 "local" 时展开原有的本地渠道列表与 API Key 配置表单（对普通用户默认隐藏自填多渠道 API Key 表单）。

### 7. 前端静态 UI 词条（`web/src/i18n/locales/zh-CN.ts` & `en-US.ts`）

- 在 zh-CN.ts 与 en-US.ts 新增 auth、wallet 及 config.channels.mode* 静态 UI 词条（严禁把后端 ErrorCodes 词条硬编码进前端 locale 文件，后端错误一律读响应里的 message）。

### 8. 单测与本地端到端实测

新增前端单测 `web/tests/pilipala-jobs.test.ts`（使用 bun test，通过注入 mock fetchImpl 测试）：

- 成功生图全链路：POST /api/v1/jobs/submit（断言请求路径为 /api/v1/jobs/submit、请求头含 pilipala-token 与 Accept-Language、请求体含 idempotency_key、capability: "image.generate"、model、params.prompt 且不含供应商 API Key）→ 轮询 GET /api/v1/jobs/{id}（queued → running → succeeded）→ GET /api/v1/jobs/{id}/result（解析 TOS 预签名 outputs[0].url）→ 返回生成的图片对象，且钱包 refreshWallet 被调用；
- 未登录拦截（UNAUTHORIZED）：POST /api/v1/jobs/submit 返回 `{ ok: false, data: null, error_code: "UNAUTHORIZED", message: "请先登录后再试。" }` → 自动将 `useAuthStore.getState().authModalOpen` 置为 true，且抛出的 `PilipalaApiError.message` 等于后端返回的 message；
- 余额不足拦截（WALLET_INSUFFICIENT）：POST /api/v1/jobs/submit 返回 `{ ok: false, data: null, error_code: "WALLET_INSUFFICIENT", message: "账户余额不足，请先充值。" }` → 抛出的错误消息等于后端 message；
- 任务失败 dead 透传友好错误：轮询返回 `{ status: "dead", error_code: "PROVIDER_UNAVAILABLE", message: "生成服务暂时不可用，请稍后重试。" }` → 抛出 PilipalaApiError，消息为后端 message；
- 取消任务闭环：轮询期间触发 `AbortController.abort()` → 自动发起 POST /api/v1/jobs/{id}/cancel 并以取消错误结束。

确保既有 bun test（含 web/tests/model-selection.test.ts、canvas-focus-viewport.test.ts、image-thumbnail.test.ts）与 bun run build（tsc -b && vite build）全部全绿通过！

### 9. 文档同步

- 将本 brief 全文保存到 `docs/engineering/feature-briefs/2026-10-24-wp7-web-pilipala-api-generation.md`。
- 更新 `docs/engineering/feature-briefs/README.md`：
  - 将 WP8、WP9 状态更新为 已实现；
  - 新增 WP7 行：`[2026-10-24-wp7-web-pilipala-api-generation.md](./2026-10-24-wp7-web-pilipala-api-generation.md) | WP7 / M2 | 已实现（待验收）`。
- 更新 `docs/engineering/runbooks/local-dev.md`：
  - 增加 WP7 前端联调章节（启动 api + worker + 本地 TOS 文件桶 + web Vite 开发服，演示在不配任何浏览器 API Key 的情况下：注册/登录拿 pilipala-token → 查看顶栏余额 → 提交生图 POST /api/v1/jobs/submit → 轮询成功并加载 TOS 签名图片 → 余额扣减对账）。

## 验收清单（PR Review 逐条对照）

- [ ] `docs/engineering/feature-briefs/2026-10-24-wp7-web-pilipala-api-generation.md` 已落仓，且 `feature-briefs/README.md`、`runbooks/local-dev.md` 已同步更新
- [ ] api/、worker/、Flyway V1–V5、docs/content/**、CHANGELOG.md 0 行改动
- [ ] web/vite.config.ts 已配置 /api 代理到 http://localhost:8080；web/src/constant/env.ts 已导出 API_BASE_URL
- [ ] 默认 channelMode 为 "remote"（托管云端模式），AppConfigPanel 默认隐藏自填多渠道 API Key 表单，仅在用户切到 "local"（本地直连非托管调试）时展示
- [ ] 在 "remote" 模式下，清空浏览器内所有本地渠道 apiKey 后，isAiConfigReady 对内置云端生图模型（gpt-image-1、dall-e-3、flux-1-schnell）返回 true，画布与图片工作台可直接触发生成
- [ ] runPilipalaImageJob 完整走通 POST /api/v1/jobs/submit（带 pilipala-token 头与 idempotency_key、capability: "image.generate"、model、params）→ 轮询 GET /api/v1/jobs/{jobId} → GET /api/v1/jobs/{jobId}/result（读取 TOS 短期签名 outputs[].url），且取消时调用 POST /api/v1/jobs/{jobId}/cancel
- [ ] 顶栏（含全局导航与画布右上角 UserStatusActions）展示登录态（username）与钱包 available / frozen 余额；未登录点生成或接口返回 UNAUTHORIZED 时自动弹出登录/注册框
- [ ] 错误提示直接采用后端返回的本地化 message（WP9 i18n_message），不向用户暴露任何堆栈或上游英文原文
- [ ] `cd web && bun test` 全部通过（含原有 3 个测试文件 + 新增 pilipala-jobs.test.ts），且 `cd web && bun run build` 零报错通过
- [ ] `./mvnw test` 后端 136 个单测保持全绿
- [ ] 分支 feat/wp7-web-pilipala-api-generation；PR base = main；无真实密钥入库；不要自行 merge

## 非目标

- 不做完整独立个人中心页面、账单明细流水页或在线支付充值弹窗（M2 充值仍走 psql 账本，后台充值界面留给 WP11）
- 不做画布项目/节点的云端数据库持久化（画布/项目/资产存 Postgres V6 属于下一个工作包 WP10 / M3）
- 不改画布节点交互体验或新增画布节点类型
- 不改 api/ 或 worker/ 后端代码

## 给 Minimax 的额外约束

- 先读再写：`DEVELOPMENT.md`（WP7）、`ARCHITECTURE.md`、ADR 0001 / 0003 / 0005、`i18n-and-errors.md`、`api/gateway-contract.md`、后端 `AuthController.java`、`JobController.java`、`WalletController.java`、前端现有 `web/src/stores/use-config-store.ts`、`web/src/services/api/image.ts`、`web/src/components/layout/app-top-nav.tsx`、`web/src/components/layout/user-status-actions.tsx`、`web/src/components/layout/app-config-modal.tsx`、`web/tests/model-selection.test.ts`。
- 千万注意 `web/tests/model-selection.test.ts`：该测试直接导入 `defaultConfig`、`resolveModelForCapability`、`useConfigStore.getState().isAiConfigReady` 测试带 channelId::modelName 前缀的渠道模型。修改 use-config-store.ts 时务必保证带 :: 前缀的模型仍严格校验 config.channels，使 model-selection.test.ts 的 8 个用例无需删改即可 100% 通过。
- 本机安装依赖提示：如果需要在 web/ 安装或校验依赖，请务必带 `--registry https://registry.npmjs.org`（不要走默认镜像）。
- 前后端全部测试与构建必须绿：`cd web && bun test && bun run build` 以及根目录 `./mvnw test` 全部通过。
- 做完用简短中文汇报：新增/修改了哪些前端文件、bun test 与 bun run build 结果、本地联调实测记录（未登录拦截 → 注册/登录拿 pilipala-token → 余额不足拦截 → 充值后提交生图 POST /api/v1/jobs/submit → 轮询拿 TOS 签名 URL 渲染 → 钱包扣费对账 → 取消任务退费）、故意没做哪些（无后端改动 / 无 WP10 云端画布持久化 / 无自助充值页）。

## 留给实现者的选择（最多拍这 1 项，PR 写明选了哪样）

本地开发环境下浏览器加载本地文件桶（http://localhost:18080）图片的 CORS 处理：

- **方式 A（推荐）**：pilipala-jobs.ts 在拿到 outputs[].url 后调 `loadImageFromUrl(url)`（若本地桶无 CORS 头导致 fetch 抛异常，自动回退为 `{ dataUrl: output.url, mimeType: "image/png", bytes: 0 }`，由 image-storage.ts 的 `new Image()` 加载宽高并直接渲染 URL），同时在 runbooks/local-dev.md 提供带 `Access-Control-Allow-Origin: *` 的一行本地桶启动说明；
- **方式 B**：在 web/vite.config.ts 中额外增加一条本地桶代理路径。

无论选哪种，确保画布与图片工作台都能正常显示生成的 PNG 图片。

不要再自行扩大其它开放问题。

## 分支 / Commit / PR 指令

```bash
git fetch origin
git checkout main
git pull origin main
git checkout -b feat/wp7-web-pilipala-api-generation
git add docs/engineering web
git commit -m "WP7: 前端生图接入 pilipala API 与登录钱包闭环"
git push -u origin feat/wp7-web-pilipala-api-generation
```

开 PR → `main`：

- 标题：`WP7: 前端生图接入 pilipala API（登录 + 钱包 + submit/query/result/cancel）`
- 正文必须含：brief 路径 `docs/engineering/feature-briefs/2026-10-24-wp7-web-pilipala-api-generation.md`、做了什么、验收自勾、故意没做（后端零改动 / 无 WP10 画布云端持久化 / 无在线充值）、本地端到端联调实测记录、实现者选择结论
- **不要自行 merge。**
