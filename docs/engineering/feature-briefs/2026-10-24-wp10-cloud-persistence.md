# Feature brief：WP10 C 端账号级云端持久化（项目 + 资产 + 生成历史 + 火山 TOS 虚拟主机兼容 + Anytoken 真实模型协议对齐）

- 日期：2026-10-24
- 作者：flora engineering
- 对应 WP / 里程碑：WP10 / M3（C 端账号级云端持久化 + 真实火山 TOS 虚拟主机签名兼容 + Anytoken 真实模型协议对齐）
- 状态：已实现（待验收）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax（上方面包屑硬约束一并保留）。不要摘要。

---

## 粘贴给 Minimax 的前置硬约束（连同下方正文一起完整粘贴）

```text
你正在实现 flora 仓库的 WP10（里程碑 M3：C 端账号级云端持久化 + 火山 TOS 虚拟主机域名兼容 + Anytoken 真实模型协议对齐）。
当前 main 已合入 WP0–WP9（最新提交为 WP7 66de5c1）。这是一份全栈（api + worker + web + docs/engineering）任务包，所有表名、字段名、类型、约束、索引、API 路径、JSON 字段名与前端文件路径均已在本 brief 中锁死，禁止自由发挥：

1. 严禁修改 V1 ~ V5 已有 Flyway 迁移文件；必须新建 V6__create_cloud_persistence.sql，表名与列定义必须与本 brief 逐字一致（canvas_project 7 列、user_asset 8 列 + PROJECT_NOT_FOUND / ASSET_NOT_FOUND 中英词条种子）。
2. 火山引擎真实公网 TOS（*.volces.com，如 https://tos-cn-guangzhou.volces.com，Bucket 为 flora-media-test / flora-media-prod）强制要求 Virtual-Host 虚拟主机风格（https://<bucket>.tos-cn-guangzhou.volces.com/<object_key>，Host: <bucket>.<endpoint-host>，canonicalUri: /<encoded-key>），对路径风格返回 403 InvalidPathAccess；而本地单测 HttpServer 使用 127.0.0.1 / localhost。必须在 api 的 TosUrlSigner 与 worker 的 TosObjectStorageAdapter 中同时实现自动识别：回环地址（127.0.0.1 / localhost / ::1 / [::1]）保持 Path-Style（保住现有单测确定性向量 100% 不变），真实域名一律走 Virtual-Host Style，并在 api 与 worker 各补齐虚拟主机风格单测。
3. 真实 Anytoken 网关（https://api.anytoken.work/v1）协议与模型列表对齐：
   - 在 worker 的 AnytokenProperties 新增可选 videoApiKey（FLORA_GATEWAY_VIDEO_API_KEY，当 capability 以 "video" 开头且非空时优先使用，否则回退 apiKey），并保留 5 参数兼容构造器（在 6 参数主构造器加 @ConstructorBinding）以保证现有 AnytokenGatewayAdapterTest 零破坏；
   - 在 AnytokenGatewayAdapter 中新增 resolveGenerateUri() 防止 baseUrl 末尾 /v1 与 generatePath 开头 /v1/ 重复拼接成 /v1/v1；requestBodyJson 同时输出顶层 prompt / n / size / quality / response_format（兼容真实 OpenAI /v1/images/generations）与原有 job_id / attempt / capability / model / params（保住现有单测断言）；collectOutputs 支持 data[] / outputs[] 中返回临时图片 url 时通过 httpClient 下载字节写入 TOS；
   - 在前端 web/src/stores/use-config-store.ts 更新 FLORA_REMOTE_IMAGE_MODELS，将真实 Anytoken 开通的 4 个生图模型（gpt-image-2, gpt-image-2.5-flare, gpt-image-2.5-sunburst, image2）排在最前，同时保留 gpt-image-1, dall-e-3, flux-1-schnell 兼容既有单测，默认生图模型设为 gpt-image-2。
4. 严守 ADR 0003 / 0004 / 0005：
   - worker 只改 TosObjectStorageAdapter 与 AnytokenGatewayAdapter/AnytokenProperties（及对应测试与 application.yml），依然零 sa-token、零 wallet、零 canvas/project 依赖；
   - 数据库只存 TOS object_key，绝不把预签名 URL 或图片 Base64 大字节落进 user_asset 或 job 表；每次查询 /api/v1/assets 与 /api/v1/jobs/history 时由 api 调 TosUrlSigner.signGetUrl(objectKey) 现签短期读地址；
   - C 端所有接口响应统一走 HTTP 200 + ApiResponse 信封（ok / data / error_code / message），失败文案一律由 I18nMessageService 解析，绝不暴露 SQL、堆栈或异常原文。
5. 前端云同步必须诚实：已登录（有 flora-token）时，画布项目（/canvas、/canvas/:id）、资产库（/assets）与生图历史（/image）走后端云端持久化接口，换浏览器登录仍能看到自己的项目、生成记录与云端素材；未登录时退回浏览器本地存储并明确标识「本地未同步」，绝不谎称已上云。
6. 严禁把任何真实密钥（火山 AK/SK 或 Anytoken sk-...）写进代码、配置文件或文档！完成后必须同时跑通 `./mvnw test` 与 `cd web && bun test && bun run build`，全部全绿后推送分支 `feat/wp10-cloud-persistence` 并创建指向 `main` 的 PR，不要自行 merge。
```

---

## 概述

本任务落地 **flora M3 核心工作包 WP10（C 端账号级云端持久化）**，并同步完成 **真实火山引擎 TOS 公网虚拟主机域名（Virtual-Host Style）签名兼容** 与 **真实 Anytoken 网关模型及 OpenAI 生图协议对齐**：

1. **Part A — 火山 TOS 虚拟主机域名兼容 + Anytoken 真实网关协议与模型对齐（`api` + `worker` + `web`）**：
   - **火山 TOS Virtual-Host 兼容**：实测火山引擎华南区公网 TOS（`https://tos-cn-guangzhou.volces.com`，Bucket `flora-media-test` / `flora-media-prod`）强制要求虚拟主机风格访问（`https://<bucket>.tos-cn-guangzhou.volces.com/<key>`），对路径风格（`https://tos-cn-guangzhou.volces.com/<bucket>/<key>`）直接返回 `403 InvalidPathAccess`。改造 `api` 的 `TosUrlSigner` 与 `worker` 的 `TosObjectStorageAdapter`：当 `endpoint` 的 host 为本地回环地址（`127.0.0.1`、`localhost`、`::1`、`[::1]`）时保持 Path-Style（`/<bucket>/<key>`），确保现有本地 `HttpServer` 单测与确定性签名向量零破坏；当 `endpoint` 为真实域名（如 `tos-cn-guangzhou.volces.com`）时自动采用 Virtual-Host Style（`scheme://<bucket>.<host>[:port]/<key>`，`Host: <bucket>.<host>[:port]`，`canonicalUri: /<encoded-key>`）。
   - **Anytoken 真实网关与模型对齐**：实测 `https://api.anytoken.work/v1/models` 生图分组开通了 `gpt-image-2`、`gpt-image-2.5-flare`、`gpt-image-2.5-sunburst`、`image2` 四个模型，视频分组开通了 `seedance2`、`seedance2.5`、`seedance-mini`、`seedance-fast` 四个模型。在 `worker` 中支持：
     1. `/v1` 路径去重拼接（当 `FLORA_GATEWAY_BASE_URL=https://api.anytoken.work/v1` 且 `generate-path` 为 `/v1/images/generations` 或 `/v1/generations` 时不会拼出 `/v1/v1`）；
     2. 请求 JSON 同时携带 OpenAI `/v1/images/generations` 所需的顶层 `prompt`、`n`、`size`（自动将 `"1:1"` 等比例转为 `"1024x1024"` 像素尺寸）、`quality`、`response_format` 以及内部契约字段 `job_id`、`attempt`、`capability`、`model`、`params`；
     3. 响应解析同时支持 `data[].b64_json` 与 `data[].url`（返回临时图片 URL 时由 `worker` 下载图片字节再存入 TOS）；
     4. `AnytokenProperties` 新增可选 `videoApiKey`（`FLORA_GATEWAY_VIDEO_API_KEY`），按 `capability` 自动选择生图 Key 或 Seedance 视频 Key；
     5. 前端 `FLORA_REMOTE_IMAGE_MODELS` 纳入真实模型并以 `gpt-image-2` 为默认云端生图模型。
2. **Part B — Flyway `V6__create_cloud_persistence.sql` 与数据模型文档锁定**：
   - 新建 `docs/engineering/data-model/projects.md`，并在 `data-model/README.md` 与 `data-model/errors.md` 同步登记；
   - 新建 `api/src/main/resources/db/migration/V6__create_cloud_persistence.sql`，创建 `canvas_project`（7 列）与 `user_asset`（8 列）两张表，并向 `i18n_message` 灌入 `PROJECT_NOT_FOUND` 与 `ASSET_NOT_FOUND`（兼容 `error.PROJECT_NOT_FOUND` / `error.ASSET_NOT_FOUND`）的中英文种子词条。
3. **Part C — 后端云端持久化接口（`com.flora.api.project` + `JobController` 历史接口）**：
   - 项目 CRUD：`POST /api/v1/projects`、`GET /api/v1/projects`、`GET /api/v1/projects/{id}`、`PUT /api/v1/projects/{id}`、`DELETE /api/v1/projects/{id}`；
   - 资产列表与同步：`GET /api/v1/assets`（自动将当前用户已成功 `succeeded` 任务的 `result_object_keys` 幂等同步进 `user_asset` 表，并对每条记录现签短期 TOS 读地址 `signed_url`）、`DELETE /api/v1/assets/{id}`；
   - 生成历史列表：`GET /api/v1/jobs/history`（以及 `GET /api/v1/jobs?limit=50`，按 `created_at DESC` 返回当前用户近期生图任务、物化 `prompt`、扣费积分 `cost_credits`、现签 `result_urls` 与友好错误文案）。
4. **Part D — 前端云端同步对接（`web/`）**：
   - 新增 `web/src/services/flora-api.ts`（及 `web/src/services/api/flora-cloud.ts`），封装 `/api/v1/projects`、`/api/v1/assets`、`/api/v1/jobs/history` 类型化客户端；
   - 改造画布项目列表页（`/canvas`）、画布工作台（`/canvas/:id`）、资产页（`/assets`）与图片页（`/image`）：已登录（存在 `flora-token`）时自动拉取并保存云端项目、展示云端生成资产与跨设备生图历史；未登录时优雅退回本地存储并明确显示本地状态，不谎称云同步已开启。

[DEVELOPMENT.md](../DEVELOPMENT.md) WP10：

> - **里程碑**：M3
> - **做什么**：项目 / 素材 / 生成记录与 `user_id` 绑定。字段级设计在独立 brief 里写，本 WP 只锁「必须离开纯浏览器本地」。
> - **不做**：把本地格式迁移写得兼容无限旧数据（产品未上线，按新设计来，除非 brief 要求）。
> - **完成**：换浏览器登录仍能看到自己的最小数据集；文档不谎称未完成的云同步。

---

## 必须遵守

- [ARCHITECTURE.md](../ARCHITECTURE.md) & [ADR 0001](../adr/0001-anytoken-not-newapi-fork.md)：C 端只打 flora API（`/api/v1/*`）；当前登录用户 ID 一律从服务端 `AuthSessionService` 会话取，**严禁信任请求体或 Query 里的 `user_id`**。
- [ADR 0003](../adr/0003-rabbitmq-first-queue-agnostic-jobs.md)：
  - TOS 签名读 URL 永远**按需现签**（`TosUrlSigner.signGetUrl(objectKey)`），**严禁把带有效期的预签名 URL 存进 `user_asset` 或 `canvas_project` 作为权威源**；
  - `user_asset` 表只存 `object_key`（如 `"anytoken/img/<job_id>/1.png"`）与元数据。
- [ADR 0004](../adr/0004-thin-worker-no-god-classes.md)：
  - `worker` 模块只改 `TosObjectStorageAdapter.java`、`AnytokenGatewayAdapter.java`、`AnytokenProperties.java`、`application.yml`（及对应单测）；
  - **`worker` 严禁引入 `canvas_project`、`user_asset`、`sa-token` 或 `wallet` 相关代码**。
- [ADR 0005](../adr/0005-friendly-errors-vs-raw-upstream.md) & [data-model/errors.md](../data-model/errors.md)：
  - 新增 `PROJECT_NOT_FOUND` 与 `ASSET_NOT_FOUND` 两个稳定错误码；
  - 查不到或不属于当前用户的项目/资产一律返回 HTTP 200 + `{"ok": false, "data": null, "error_code": "PROJECT_NOT_FOUND" | "ASSET_NOT_FOUND", "message": "..."}`（不泄露他人资源存在性）。
- [CONTRIBUTING.md](../CONTRIBUTING.md)：
  - **严禁修改 `V1` ~ `V5` 已有迁移文件**；
  - 不改 `docs/content/**` 与 `CHANGELOG.md`。

---

## 本 WP 拍板（已决定，禁止再猜）

| 项 | 锁定值 |
| --- | --- |
| 分支名 | `feat/wp10-cloud-persistence` |
| 火山 TOS 虚拟主机 vs 路径风格判定规则 | 从 `URI.create(endpointBase())` 提取 `host`：<br>1. 若 `host`（大小写不敏感）为 `"127.0.0.1"`、`"localhost"`、`"::1"` 或 `"[::1]"`（本地回环地址）：使用 **Path-Style**：<br>   - `hostHeader = host + nonDefaultPort`<br>   - `canonicalUri = "/" + bucket + "/" + TosV4Signer.uriEncodePath(key)`<br>   - `requestUrl = scheme + "://" + hostHeader + canonicalUri`<br>2. 其它所有真实域名（如 `tos-cn-guangzhou.volces.com`、`tos-cn-beijing.volces.com`）：使用 **Virtual-Host Style**：<br>   - `virtualHost = host.toLowerCase(Locale.ROOT).startsWith(bucket.toLowerCase(Locale.ROOT) + ".") ? host : bucket + "." + host`<br>   - `hostHeader = virtualHost + nonDefaultPort`<br>   - `canonicalUri = "/" + TosV4Signer.uriEncodePath(key)`<br>   - `requestUrl = scheme + "://" + hostHeader + canonicalUri`<br>（`api` 的 `TosUrlSigner` 与 `worker` 的 `TosObjectStorageAdapter` 严格共用此规则） |
| Anytoken 真实网关兼容与模型列表 | 1. `AnytokenProperties` 新增可选 `videoApiKey`（配置项 `flora.gateway.video-api-key: ${FLORA_GATEWAY_VIDEO_API_KEY:}`），保留 5 参数构造器并在 6 参数主构造器标注 `@ConstructorBinding`；<br>2. `AnytokenGatewayAdapter` 新增 `resolveGenerateUri()` 自动消除 `baseUrl` 末尾 `/v1` 与 `generatePath` 开头 `/v1/` 的重复；`requestBodyJson` 同时输出顶层 `prompt`、`n`、`size`（`"1:1"`→`"1024x1024"`、`"16:9"`/`"3:2"`→`"1536x1024"`、`"9:16"`/`"2:3"`→`"1024x1536"`）、`quality`、`response_format`（非 `gpt-image*` 模型传 `"b64_json"`）及原有 `job_id`、`attempt`、`capability`、`model`、`params`；`collectOutputs` 支持 `data[]` / `outputs[]` 节点含 `"url"` 时下载图片字节写入 TOS；<br>3. 前端 `FLORA_REMOTE_IMAGE_MODELS` 设为 `gpt-image-2`、`gpt-image-2.5-flare`、`gpt-image-2.5-sunburst`、`image2`、`gpt-image-1`、`dall-e-3`、`flux-1-schnell`，默认 `model` 与 `imageModel` 设为 `"gpt-image-2"` |
| 迁移文件 `V6` | `api/src/main/resources/db/migration/V6__create_cloud_persistence.sql`：建 `canvas_project`（7 列）+ `user_asset`（8 列）+ 索引 + `i18n_message` 新错误码中英种子。**`V1`–`V5` 必须 0 行改动** |
| 新增稳定错误码 | 在 `ErrorCodes.java`、`I18nDefaultCatalog.java`、`data-model/errors.md`、`V6__create_cloud_persistence.sql` 中登记：<br>- `PROJECT_NOT_FOUND`：`zh`=`"项目不存在。"` / `en`=`"The project does not exist."`<br>- `ASSET_NOT_FOUND`：`zh`=`"素材不存在。"` / `en`=`"The asset does not exist."`<br>（`V6` SQL 中同时插入 `key = 'PROJECT_NOT_FOUND'`、`'ASSET_NOT_FOUND'` 以及 `'error.PROJECT_NOT_FOUND'`、`'error.ASSET_NOT_FOUND'`，确保两种键名查询均命中） |
| `GET /api/v1/assets` 自动同步机制 | 调用 `GET /api/v1/assets` 时，先在 `AssetService.listUserAssets(userId)` 内查询该用户所有 `status = 'succeeded'` 的 `job`，将其 `result_object_keys` JSON 数组中的每个 `object_key` 幂等写入 `user_asset`（`source = 'generated'`, `ON CONFLICT (job_id, object_key) DO NOTHING`），再按 `created_at DESC` 返回用户的 `user_asset` 列表（默认最多 100 条），每条记录现签 `signed_url = tosUrlSigner.signGetUrl(objectKey)` |
| `GET /api/v1/jobs/history`（兼容 `GET /api/v1/jobs`） | 在 `JobController` 新增 `@GetMapping({"/history", ""})`（支持 `?limit=50`，范围 `1..100`，默认 `50`）：按 `created_at DESC` 返回当前用户的任务列表，含 `job_id`、`status`、`capability`、`model`、`prompt`（从 `params.prompt` 提取）、`cost_credits`、`result_urls`（仅 `succeeded` 时为每个 `object_key` 现签 URL，其余为空列表 `[]`）、`error_code`、`message`（按当前 locale 解析）、`created_at`、`updated_at`、`finished_at`（终态时取 `updated_at`，非终态为 `null`） |
| 前端云同步与本地回退策略 | 1. **已登录（`useAuthStore.getState().token` 非空）**：<br>   - 进入 `/canvas` 或登录成功后，调 `GET /api/v1/projects` 拉取云端项目列表并同步进 `useCanvasStore`；<br>   - 新建项目调 `POST /api/v1/projects`（以返回的云端 UUID 作为项目 `id`）；<br>   - 打开 `/canvas/:id` 时若本地无 `canvas_data` 或版本较旧，调 `GET /api/v1/projects/{id}` 拉取完整画布快照；编辑画布/重命名时防抖（或点顶栏「云端保存」按钮）调 `PUT /api/v1/projects/{id}` 持久化到 Postgres；删除项目调 `DELETE /api/v1/projects/{id}`；<br>   - 进入 `/assets` 或 `/image` 时调 `GET /api/v1/assets` 与 `GET /api/v1/jobs/history` 展示云端生成的素材与跨浏览器生图历史。<br>2. **未登录（无 `flora-token`）**：保持原有 `localForage` 本地存储行为，并在界面显示「本地模式（登录后开启云同步）」提示，不发未授权请求风暴，也不谎称已云同步。 |

---

## Part A：火山 TOS 虚拟主机风格兼容 + Anytoken 真实网关协议对齐（`api` + `worker` + `web`）

### 1. 火山 TOS 虚拟主机风格（Virtual-Host Style）修复（`api` + `worker`）

在真实火山引擎 TOS 环境（`FLORA_TOS_ENDPOINT=https://tos-cn-guangzhou.volces.com`，`FLORA_TOS_REGION=cn-guangzhou`，`FLORA_TOS_BUCKET=flora-media-test` / `flora-media-prod`）下：
- 若请求 `https://tos-cn-guangzhou.volces.com/flora-media-test/<key>`（路径风格），火山 TOS 网关拒绝并返回 `HTTP 403 {"Code":"InvalidPathAccess","Message":"Path style access is not allowed"}`；
- 必须请求 `https://flora-media-test.tos-cn-guangzhou.volces.com/<key>`（虚拟主机风格），且参与 V4 签名的 `Host` 头为 `flora-media-test.tos-cn-guangzhou.volces.com`、`CanonicalURI` 为 `/<uri-encoded-key>`。
- 同时，本地单测使用 `HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)`，地址为 `http://127.0.0.1:<port>`，无法绑定子域名，必须保留 Path-Style。

#### (1) `api/src/main/java/com/flora/api/storage/TosUrlSigner.java` 改造

在 `TosUrlSigner` 中将 URL 与 `host` / `canonicalUri` 组装逻辑更新为：

```java
    static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        String lower = host.trim().toLowerCase(java.util.Locale.ROOT);
        return "127.0.0.1".equals(lower)
                || "localhost".equals(lower)
                || "::1".equals(lower)
                || "[::1]".equals(lower);
    }
```

当签发 `signGetUrl(String objectKey, Instant signingTime)` 时：
1. 解析 `URI uri = URI.create(endpointBase())`，取 `String scheme = uri.getScheme()`、`String rawHost = uri.getHost()`、`int port = uri.getPort()`（若 `scheme == null || rawHost == null || rawHost.isBlank()` 抛 `IllegalStateException`）。
2. 判断 `boolean pathStyle = isLoopbackHost(rawHost)`：
   - **若 `pathStyle == true`（回环地址）**：
     - `String host = formatHostPort(rawHost, port);`
     - `String canonicalUri = "/" + properties.bucket() + "/" + TosV4Signer.uriEncodePath(key);`
     - `String baseUrl = scheme + "://" + host;`
   - **若 `pathStyle == false`（真实 TOS 域名，如 `tos-cn-guangzhou.volces.com`）**：
     - `String bucketPrefix = properties.bucket() + ".";`
     - `String virtualHost = rawHost.regionMatches(true, 0, bucketPrefix, 0, bucketPrefix.length()) ? rawHost : bucketPrefix + rawHost;`
     - `String host = formatHostPort(virtualHost, port);`
     - `String canonicalUri = "/" + TosV4Signer.uriEncodePath(key);`
     - `String baseUrl = scheme + "://" + host;`
3. 最终返回：`baseUrl + canonicalUri + "?" + canonicalQueryString + "&X-Tos-Signature=" + signature`。
   其中 `formatHostPort(String host, int port)` 在 `port != -1 && port != 80 && port != 443` 时返回 `host + ":" + port`，否则返回 `host`。

#### (2) `worker/src/main/java/com/flora/worker/storage/TosObjectStorageAdapter.java` 改造

在 `TosObjectStorageAdapter` 的 `putToTos(String key, byte[] content, String contentType)` 中采用**完全相同**的 `isLoopbackHost` 判定逻辑：
- 当 `isLoopbackHost(rawHost)` 为 `true` 时：
  - `host = formatHostPort(rawHost, port)`
  - `canonicalUri = "/" + properties.bucket() + "/" + TosV4Signer.uriEncodePath(key)`
  - `targetUri = URI.create(scheme + "://" + host + canonicalUri)`
- 当 `isLoopbackHost(rawHost)` 为 `false` 时：
  - `virtualHost = rawHost.regionMatches(true, 0, bucketPrefix, 0, bucketPrefix.length()) ? rawHost : bucketPrefix + rawHost`
  - `host = formatHostPort(virtualHost, port)`
  - `canonicalUri = "/" + TosV4Signer.uriEncodePath(key)`
  - `targetUri = URI.create(scheme + "://" + host + canonicalUri)`

#### (3) `TosUrlSignerTest` 与 `TosObjectStorageAdapterTest` 单测补充

- 在 `api/src/test/java/com/flora/api/storage/TosUrlSignerTest.java`：
  - 保留原有 `signedUrlMatchesTheDeterministicVector`（`http://127.0.0.1:18080` 路径风格断言一字不改）；
  - 新增 `virtualHostStyleUsedForRealVolcesDomain` 测试：当 `endpoint = "https://tos-cn-guangzhou.volces.com"`、`region = "cn-guangzhou"`、`bucket = "flora-media-test"` 时，断言签出的 URL 以 `"https://flora-media-test.tos-cn-guangzhou.volces.com/anytoken/img/11111111-2222-3333-4444-555555555555/1.png?"` 开头（不含重复的 `/flora-media-test/` 路径前缀），且签名与按 `host:flora-media-test.tos-cn-guangzhou.volces.com` + `canonicalUri=/anytoken/img/...` 独立计算的期望签名完全一致。
- 在 `worker/src/test/java/com/flora/worker/storage/TosObjectStorageAdapterTest.java`：
  - 保留原有全部 `127.0.0.1` 测试；
  - 新增验证非回环域名（如 `"https://tos-cn-guangzhou.volces.com"`）目标请求 URI 与签名参数解析的单元测试（可通过包可见辅助方法断言 `targetUri` 为 `"https://flora-media-test.tos-cn-guangzhou.volces.com/anytoken/img/x/1.png"`、`hostHeader` 为 `"flora-media-test.tos-cn-guangzhou.volces.com"`、`canonicalUri` 为 `"/anytoken/img/x/1.png"`）。

---

### 2. Anytoken 真实网关协议与模型对齐（`worker` + `web`）

#### (1) `worker/src/main/java/com/flora/worker/gateway/AnytokenProperties.java` 与 `application.yml`

- 更新 `AnytokenProperties.java`，新增 `videoApiKey`（支持为生图分组与 Seedance 视频分组分别注入 Key，同时保留 5 参数构造器并在主构造器加上 `@ConstructorBinding`）：
  ```java
  package com.flora.worker.gateway;

  import java.util.Locale;
  import org.springframework.boot.context.properties.ConfigurationProperties;
  import org.springframework.boot.context.properties.bind.ConstructorBinding;

  @ConfigurationProperties(prefix = "flora.gateway")
  public record AnytokenProperties(
          String mode,
          String baseUrl,
          String apiKey,
          String videoApiKey,
          long timeoutMs,
          String generatePath) {

      @ConstructorBinding
      public AnytokenProperties {
      }

      public AnytokenProperties(String mode, String baseUrl, String apiKey, long timeoutMs, String generatePath) {
          this(mode, baseUrl, apiKey, "", timeoutMs, generatePath);
      }

      public String apiKeyForCapability(String capability) {
          if (capability != null
                  && capability.toLowerCase(Locale.ROOT).startsWith("video")
                  && videoApiKey != null
                  && !videoApiKey.isBlank()) {
              return videoApiKey;
          }
          return apiKey;
      }
  }
  ```
- 在 `worker/src/main/resources/application.yml` 的 `flora.gateway` 下补充 `video-api-key` 并将默认 `generate-path` 设为 `/v1/images/generations`：
  ```yaml
  flora:
    gateway:
      mode: ${FLORA_GATEWAY_MODE:stub}
      base-url: ${FLORA_GATEWAY_BASE_URL:http://127.0.0.1:18080}
      api-key: ${FLORA_GATEWAY_API_KEY:}
      video-api-key: ${FLORA_GATEWAY_VIDEO_API_KEY:}
      timeout-ms: ${FLORA_GATEWAY_TIMEOUT_MS:30000}
      generate-path: ${FLORA_GATEWAY_GENERATE_PATH:/v1/images/generations}
  ```

#### (2) `worker/src/main/java/com/flora/worker/gateway/AnytokenGatewayAdapter.java` 增强

1. **URL 拼接去重（`resolveGenerateUri()`）**：
   - 当用户配置 `FLORA_GATEWAY_BASE_URL=https://api.anytoken.work/v1`（或 `https://api.anytoken.work`）且 `generatePath` 为 `/v1/images/generations`（或 `/v1/generations`）时，自动消除重复的 `/v1`：
     ```java
     URI resolveGenerateUri() {
         String base = (properties.baseUrl() == null ? "" : properties.baseUrl().trim()).replaceAll("/+$", "");
         String path = (properties.generatePath() == null || properties.generatePath().isBlank())
                 ? "/v1/images/generations"
                 : properties.generatePath().trim();
         if (!path.startsWith("/")) {
             path = "/" + path;
         }
         if (base.toLowerCase(java.util.Locale.ROOT).endsWith("/v1")
                 && path.toLowerCase(java.util.Locale.ROOT).startsWith("/v1/")) {
             path = path.substring(3);
         }
         return URI.create(base + path);
     }
     ```
   - `http(AnytokenRequest request)` 中使用 `resolveGenerateUri()`，并用 `String effectiveKey = properties.apiKeyForCapability(request.capability())` 设置 `Authorization: Bearer <effectiveKey>`。
2. **兼容标准 OpenAI `/v1/images/generations` 请求体（`requestBodyJson`）**：
   - 保留顶层 `"job_id"`、`"attempt"`、`"capability"`、`"model"`、`"params"`（确保既有 `AnytokenGatewayAdapterTest` 100% 通过）；
   - 同时将 `params` 中的生图字段提升到顶层，使真实 `https://api.anytoken.work/v1/images/generations` 直接识别：
     - 若 `params.path("prompt").asText("")` 非空，写入顶层 `node.put("prompt", prompt)`；
     - `node.put("n", Math.max(1, params.path("n").asInt(1)))`；
     - 若 `normalizeOpenAiImageSize(params.path("size").asText(""))` 非空，写入顶层 `node.put("size", normalizedSize)`（其中 `"1:1"` → `"1024x1024"`，`"16:9"` / `"3:2"` → `"1536x1024"`，`"9:16"` / `"2:3"` → `"1024x1536"`，形如 `"1024x1024"` 的 `^\d+x\d+$` 原样保留，`"auto"` 或空串则不写顶层 `size`）；
     - 若 `params.path("quality").asText("")` 为 `"low"` / `"medium"` / `"high"`（非 `"auto"` 且非空），写入顶层 `node.put("quality", quality)`；
     - 若 `request.model()` 不含 `"gpt-image"`（大小写不敏感，因为 `gpt-image-*` 默认返回 base64 且部分上游拒绝显式 `response_format` 参数），写入 `node.put("response_format", "b64_json")`。
3. **支持上游 `data[].url` 临时图片下载落盘 TOS（`collectOutputs`）**：
   - 在 `collectOutputs(JsonNode array, AnytokenRequest request, List<StoredOutput> outputs)` 中：
     - 当元素含非空 `b64_json` 时，按原逻辑 `Base64.getDecoder().decode(base64)`；
     - 当元素不含 `b64_json`，但含以 `http://` 或 `https://` 开头的 `url` 字段（如 `{"data": [{"url": "https://..."}]}`）时，调用 `downloadImageBytes(url)` 通过 `httpClient` 拉取图片字节（若 HTTP 非 2xx、空字节或 IO 异常，抛 `IllegalArgumentException("Failed to download upstream image url")` 交由 `storeHttpResult` 归一为可重试的 `PROVIDER_UNAVAILABLE`），写入 `outputs`。
4. **补充 `AnytokenGatewayAdapterTest` 单测**：
   - 新增测试验证：
     1. `resolveGenerateUri()` 对 `baseUrl = "https://api.anytoken.work/v1"` + `generatePath = "/v1/images/generations"` 归一为 `"https://api.anytoken.work/v1/images/generations"`；
     2. `http` 请求体同时包含顶层 `prompt`、`size`（`"1:1"` → `"1024x1024"`）与 `job_id`、`params`；
     3. 当上游返回 `{"data":[{"url":"http://127.0.0.1:<port>/generated.png"}]}` 时，worker 自动下载该图片字节并写入 TOS；
     4. 当 `capability = "video.generate"` 且配置了 `videoApiKey` 时，请求头携带 `Bearer <videoApiKey>`。

#### (3) 前端云端生图模型列表更新（`web/src/stores/use-config-store.ts` & `web/src/services/api/flora-jobs.ts` & `web/tests/model-selection.test.ts`）

- 在 `web/src/stores/use-config-store.ts`：
  - 将 `FLORA_REMOTE_IMAGE_MODELS` 更新为（前 4 个为真实 Anytoken 生图模型，后 3 个保留作向后兼容）：
    ```ts
    export const FLORA_REMOTE_IMAGE_MODELS: ChannelModel[] = [
        { name: "gpt-image-2", capability: "image" },
        { name: "gpt-image-2.5-flare", capability: "image" },
        { name: "gpt-image-2.5-sunburst", capability: "image" },
        { name: "image2", capability: "image" },
        { name: "gpt-image-1", capability: "image" },
        { name: "dall-e-3", capability: "image" },
        { name: "flux-1-schnell", capability: "image" },
    ];
    ```
  - 将 `defaultConfig.model` 与 `defaultConfig.imageModel` 设为 `"gpt-image-2"`。
- 在 `web/src/services/api/flora-jobs.ts`：
  - 默认兜底模型名设为 `"gpt-image-2"`：`const model = modelOptionName(config.model || config.imageModel || "gpt-image-2") || "gpt-image-2";`。
- 在 `web/tests/model-selection.test.ts`：
  - 更新最后一个用例 `the managed cloud mode resolves an image capability to a built-in cloud model`：因为 `"gpt-image-2"` 现已加入 `FLORA_REMOTE_IMAGE_MODELS` 并成为首位默认模型，断言 `resolveModelForCapability(managed, "gpt-image-2", "image")` 返回 `"gpt-image-2"`，未知非前缀模型 `resolveModelForCapability(managed, "legacy-local-only", "image")` 回退为 `"gpt-image-2"`，`resolveModelForCapability(managed, "dall-e-3", "image")` 返回 `"dall-e-3"`。

---

## Part B：数据库 Schema 与 Flyway `V6__create_cloud_persistence.sql`

### 1. 数据模型文档：`docs/engineering/data-model/projects.md`（新建）

新建 `docs/engineering/data-model/projects.md`，锁死 WP10 两张表的字段级定义：

#### 表 1：`canvas_project`（7 列）

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK `DEFAULT gen_random_uuid()` | 项目唯一标识 |
| `user_id` | UUID | NO | FK → `users(id)` | 所属用户；只允许本人读写删 |
| `name` | TEXT | NO | | 项目名称（1..128 字符） |
| `canvas_data` | JSONB | NO | `DEFAULT '{}'::jsonb` | 画布完整快照（`nodes`、`connections`、`viewport`、`backgroundMode`、`showImageInfo`、`chatSessions`、`activeChatId`） |
| `version` | BIGINT | NO | `DEFAULT 1 CHECK (version >= 1)` | 乐观递增版本号，每次 `PUT` 更新自动 `+ 1` |
| `created_at` | TIMESTAMPTZ | NO | `DEFAULT now()` | 创建时间 |
| `updated_at` | TIMESTAMPTZ | NO | `DEFAULT now()` | 最后保存时间 |

索引：
- `idx_canvas_project_user_updated ON canvas_project (user_id, updated_at DESC)`

#### 表 2：`user_asset`（8 列）

| 字段 | 类型 | 空 | 约束 / 索引 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | UUID | NO | PK `DEFAULT gen_random_uuid()` | 资产唯一标识 |
| `user_id` | UUID | NO | FK → `users(id)` | 所属用户 |
| `project_id` | UUID | YES | FK → `canvas_project(id) ON DELETE SET NULL` | 关联画布项目（可选，删除项目不删资产） |
| `job_id` | UUID | YES | FK → `job(id) ON DELETE SET NULL` | 关联生成任务（`source='generated'` 时指向产生该图的 `job.id`） |
| `source` | TEXT | NO | `CHECK (source IN ('generated', 'uploaded'))` | 资产来源：`generated`（生图落盘）或 `uploaded`（用户上传/登记） |
| `object_key` | TEXT | NO | | 火山 TOS 对象键（如 `anytoken/img/<job_id>/1.png`）；**严禁存预签名 URL 或 Base64** |
| `mime_type` | TEXT | NO | `DEFAULT 'image/png'` | MIME 类型 |
| `created_at` | TIMESTAMPTZ | NO | `DEFAULT now()` | 创建时间 |

约束与索引：
- `CONSTRAINT uk_user_asset_job_object UNIQUE (job_id, object_key)`（保证同一 `job_id` + `object_key` 多次同步幂等不重）
- `idx_user_asset_user_created ON user_asset (user_id, created_at DESC)`

### 2. Flyway 迁移文件：`api/src/main/resources/db/migration/V6__create_cloud_persistence.sql`（新建）

**严禁修改 `V1` ~ `V5`**。新建 `V6__create_cloud_persistence.sql`，内容如下：

```sql
-- WP10: Cloud persistence domain (canvas_project + user_asset) and i18n error seeds
-- Ref: docs/engineering/data-model/projects.md, docs/engineering/data-model/errors.md

CREATE TABLE canvas_project (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id),
    name        TEXT        NOT NULL,
    canvas_data JSONB       NOT NULL DEFAULT '{}'::jsonb,
    version     BIGINT      NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_canvas_project_user_updated ON canvas_project (user_id, updated_at DESC);

CREATE TABLE user_asset (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id),
    project_id  UUID        NULL REFERENCES canvas_project(id) ON DELETE SET NULL,
    job_id      UUID        NULL REFERENCES job(id) ON DELETE SET NULL,
    source      TEXT        NOT NULL CHECK (source IN ('generated', 'uploaded')),
    object_key  TEXT        NOT NULL,
    mime_type   TEXT        NOT NULL DEFAULT 'image/png',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_user_asset_job_object UNIQUE (job_id, object_key)
);

CREATE INDEX idx_user_asset_user_created ON user_asset (user_id, created_at DESC);

INSERT INTO i18n_message (key, locale, template) VALUES
    ('PROJECT_NOT_FOUND',       'zh', '项目不存在。'),
    ('PROJECT_NOT_FOUND',       'en', 'The project does not exist.'),
    ('ASSET_NOT_FOUND',         'zh', '素材不存在。'),
    ('ASSET_NOT_FOUND',         'en', 'The asset does not exist.'),
    ('error.PROJECT_NOT_FOUND', 'zh', '项目不存在。'),
    ('error.PROJECT_NOT_FOUND', 'en', 'The project does not exist.'),
    ('error.ASSET_NOT_FOUND',   'zh', '素材不存在。'),
    ('error.ASSET_NOT_FOUND',   'en', 'The asset does not exist.')
ON CONFLICT (key, locale) DO NOTHING;
```

### 3. 更新 `ErrorCodes.java` 与 `I18nDefaultCatalog.java`

- 在 `api/src/main/java/com/flora/api/common/ErrorCodes.java` 新增：
  ```java
  /** WP10 起启用：项目不存在或不属于当前用户。 */
  public static final String PROJECT_NOT_FOUND = "PROJECT_NOT_FOUND";
  /** WP10 起启用：素材不存在或不属于当前用户。 */
  public static final String ASSET_NOT_FOUND = "ASSET_NOT_FOUND";
  ```
- 在 `api/src/main/java/com/flora/api/i18n/application/I18nDefaultCatalog.java` 的 `SEEDS` 中新增 `PROJECT_NOT_FOUND` 与 `ASSET_NOT_FOUND`（使用 `Map.ofEntries(...)` 替换超过 10 对上限的 `Map.of(...)`）：
  - `ErrorCodes.PROJECT_NOT_FOUND`: `zh` = `"项目不存在。"`, `en` = `"The project does not exist."`
  - `ErrorCodes.ASSET_NOT_FOUND`: `zh` = `"素材不存在。"`, `en` = `"The asset does not exist."`

---

## Part C：后端 API 与应用层设计（`com.flora.api.project` & `com.flora.api.job`）

### 1. 包结构：`api/src/main/java/com/flora/api/project/`

保持与 `auth`、`job`、`billing`、`i18n` 完全一致的轻量分层，禁止造上帝类：

```text
api/src/main/java/com/flora/api/project/
├── domain/
│   ├── AssetSource.java                # enum: GENERATED("generated"), UPLOADED("uploaded")
│   ├── CanvasProject.java              # record(UUID id, UUID userId, String name, String canvasDataJson, long version, Instant createdAt, Instant updatedAt)
│   └── UserAsset.java                  # record(UUID id, UUID userId, UUID projectId, UUID jobId, AssetSource source, String objectKey, String mimeType, Instant createdAt)
├── infra/
│   ├── CanvasProjectRepository.java    # 接口
│   ├── JdbcCanvasProjectRepository.java# @Repository JDBC 实现（SQL 仅在此类）
│   ├── UserAssetRepository.java        # 接口
│   ├── JdbcUserAssetRepository.java    # @Repository JDBC 实现（SQL 仅在此类）
│   └── ProjectException.java           # 运行时异常（携带 errorCode: PROJECT_NOT_FOUND / ASSET_NOT_FOUND / VALIDATION_ERROR）
├── application/
│   ├── ProjectSummaryView.java         # record(String id, String name, long version, Instant createdAt, Instant updatedAt)
│   ├── ProjectDetailView.java          # record(String id, String name, JsonNode canvasData, long version, Instant createdAt, Instant updatedAt)
│   ├── DeleteProjectView.java          # record(String id, boolean deleted)
│   ├── UserAssetView.java              # record(String id, String projectId, String jobId, String source, String objectKey, String signedUrl, String mimeType, Instant createdAt)
│   ├── ProjectService.java             # @Service：create / list / get / update / delete 项目
│   └── AssetService.java               # @Service：syncFromSucceededJobs + listUserAssets + deleteAsset
└── web/
    ├── ProjectController.java          # @RestController @RequestMapping("/api/v1/projects")
    ├── AssetController.java            # @RestController @RequestMapping("/api/v1/assets")
    └── ProjectExceptionHandler.java    # @RestControllerAdvice @Order(Ordered.HIGHEST_PRECEDENCE) extends FriendlyErrorHandler
```

### 2. 接口与 DTO 详细规格（JSON 恒为 `snake_case`，HTTP 200 统一信封）

#### (1) `POST /api/v1/projects` — 创建云端画布项目
- 请求体 `CreateProjectRequest(String name, JsonNode canvasData)`：
  - `name`：必填，去首尾空白后长度 `1..128`，否则抛 `ProjectException.validation()`（`VALIDATION_ERROR`）；
  - `canvas_data`：可选，若为 `null` 则存 `"{}"`，否则必须为 JSON Object（`canvasData.isObject()`，且序列化后长度不超过 `2_000_000` 字符），否则抛 `ProjectException.validation()`。
- 响应 `data`（`ProjectDetailView`）：
  ```json
  {
    "id": "c4a760a8-...",
    "name": "未命名画布 1",
    "canvas_data": { "nodes": [], "connections": [], "viewport": { "x": 0, "y": 0, "k": 1 } },
    "version": 1,
    "created_at": "2026-10-24T06:00:00Z",
    "updated_at": "2026-10-24T06:00:00Z"
  }
  ```

#### (2) `GET /api/v1/projects` — 列出当前用户的项目摘要列表
- 按 `updated_at DESC` 排序，最多返回 100 条。
- **不返回大字段 `canvas_data`**，只返回轻量摘要 `List<ProjectSummaryView>`：
  ```json
  [
    {
      "id": "c4a760a8-...",
      "name": "未命名画布 1",
      "version": 2,
      "created_at": "2026-10-24T06:00:00Z",
      "updated_at": "2026-10-24T06:05:00Z"
    }
  ]
  ```

#### (3) `GET /api/v1/projects/{projectId}` — 获取单个项目详情（含 `canvas_data`）
- 校验 `projectId` 为合法 UUID 且 `user_id = currentUserId()`；若格式非法、不存在或属于其他用户，一律抛 `ProjectException.projectNotFound()` → 返回：
  ```json
  {
    "ok": false,
    "data": null,
    "error_code": "PROJECT_NOT_FOUND",
    "message": "项目不存在。"
  }
  ```
- 成功返回 `ProjectDetailView`。

#### (4) `PUT /api/v1/projects/{projectId}` — 更新项目名称与/或画布快照
- 请求体 `UpdateProjectRequest(String name, JsonNode canvasData)`：
  - `name` 与 `canvas_data` 至少提供一个非空字段，否则抛 `ProjectException.validation()`；
  - 若提供 `name`，trim 后长度须为 `1..128`；
  - 若提供 `canvas_data`，须为 JSON Object（`isObject()`）。
- 仅当 `id = projectId AND user_id = currentUserId()` 时更新：
  - `version = version + 1`
  - `updated_at = now()`
- 若不存在或不属于当前用户，抛 `ProjectException.projectNotFound()`（`PROJECT_NOT_FOUND`）。
- 成功返回更新后的 `ProjectDetailView`（`version` 已 `+ 1`）。

#### (5) `DELETE /api/v1/projects/{projectId}` — 删除项目
- 仅当 `id = projectId AND user_id = currentUserId()` 时删除；若影响行数为 0（不存在或属于他人），抛 `ProjectException.projectNotFound()`（`PROJECT_NOT_FOUND`）。
- 成功返回 `DeleteProjectView`：`{"id": "<projectId>", "deleted": true}`。

#### (6) `GET /api/v1/assets` — 获取当前用户的云端资产列表（含自动同步已成功任务输出 + 现签 TOS URL）
- 支持可选参数 `?limit=100`（范围 `1..100`，默认 `100`）。
- **自动同步步骤（`AssetService.syncFromSucceededJobs(UUID userId)`）**：
  - 查询当前用户 `status = 'succeeded'` 且 `result_object_keys IS NOT NULL` 的最近任务（如最近 100 条）；
  - 解析 `result_object_keys` JSON 数组中的每个非空 `objectKey`，调用 `userAssetRepository.insertGeneratedIfAbsent(userId, job.id(), objectKey, "image/png")`（底层 SQL：`INSERT INTO user_asset (user_id, job_id, source, object_key, mime_type) VALUES (?, ?, 'generated', ?, ?) ON CONFLICT (job_id, object_key) DO NOTHING`）。
- **查询与签名步骤**：
  - 查询 `user_asset WHERE user_id = ? ORDER BY created_at DESC LIMIT ?`；
  - 对每条 `UserAsset`，调用 `tosUrlSigner.signGetUrl(asset.objectKey())` 生成短期有效读地址 `signed_url`（若某条 `objectKey` 非法导致签名抛 `IllegalArgumentException`，记录 WARN 日志并将 `signedUrl` 置为 `null`，不阻断列表）；
  - 返回 `List<UserAssetView>`：
    ```json
    [
      {
        "id": "a1b2c3d4-...",
        "project_id": null,
        "job_id": "11111111-...",
        "source": "generated",
        "object_key": "anytoken/img/11111111-.../1.png",
        "signed_url": "https://flora-media-test.tos-cn-guangzhou.volces.com/anytoken/img/...?X-Tos-Algorithm=TOS4-HMAC-SHA256&...",
        "mime_type": "image/png",
        "created_at": "2026-10-24T06:10:00Z"
      }
    ]
    ```

#### (7) `DELETE /api/v1/assets/{assetId}` — 删除单条云端资产记录
- 校验 `assetId` 属于 `currentUserId()`，否则抛 `ProjectException.assetNotFound()`（`ASSET_NOT_FOUND`）。
- 成功返回 `{"id": "<assetId>", "deleted": true}`。

#### (8) `GET /api/v1/jobs/history`（兼容 `GET /api/v1/jobs`）— 获取当前用户的生图任务历史
- 在 `JobRepository`（及 `JdbcJobRepository`、`InMemoryJobStore`）新增：
  ```java
  List<Job> findByUserOrderByCreatedAtDesc(UUID userId, int limit);
  ```
  SQL：`SELECT <COLUMNS> FROM job WHERE user_id = ? ORDER BY created_at DESC LIMIT ?`。
- 在 `com.flora.api.job.application` 新增 `ListUserJobs` 用例与 `JobHistoryItemView` DTO：
  ```java
  public record JobHistoryItemView(
          String jobId,
          String status,
          String capability,
          String model,
          String prompt,
          long costCredits,
          List<String> resultUrls,
          String errorCode,
          String message,
          Instant createdAt,
          Instant updatedAt,
          Instant finishedAt) {
  }
  ```
  - 对每条 `Job`：
    - 调用 `holds.settle(job.holdId(), job.status())` 确保已进终态的任务完成钱包结算；
    - 从 `job.paramsJson()` 提取 `params.path("prompt").asText("")` 作为 `prompt`；
    - `costCredits` 由 `costPolicy.costOf(job.capability(), job.model())` 计算；
    - 当 `job.status() == JobStatus.SUCCEEDED` 时，解析 `job.resultObjectKeysJson()` 并用 `tosUrlSigner.signGetUrl(key)` 生成 `resultUrls`；非 `SUCCEEDED` 时 `resultUrls` 为 `List.of()`；
    - 当 `job.errorCode() != null` 时，调用 `i18n.resolve(job.errorCode(), locale)` 填入友好 `message`；
    - 当 `job.status().isTerminal()`（`succeeded` / `cancelled` / `dead`）时，`finishedAt = job.updatedAt()`，否则 `finishedAt = null`。
- 在 `JobController` 新增端点（同时匹配 `/api/v1/jobs/history` 与 `/api/v1/jobs`）：
  ```java
  @GetMapping({"/history", ""})
  public ApiResponse<List<JobHistoryItemView>> history(
          @RequestParam(value = "limit", defaultValue = "50") int limit,
          @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
      UUID userId = currentUserId();
      return ApiResponse.ok(listUserJobs.list(userId, limit, locale(userId, acceptLanguage)));
  }
  ```
  （注：`limit` 在 `ListUserJobs` 内 clamp 到 `1..100`，若 `limit <= 0` 或 `> 100` 可 clamp 为 `Math.max(1, Math.min(100, limit))`）。

---

## Part D：前端云端同步设计（`web/`）

### 1. 新增云端持久化 API 客户端：`web/src/services/flora-api.ts`（与 `web/src/services/api/flora-cloud.ts`）

新建 `web/src/services/api/flora-cloud.ts`，并在 `web/src/services/flora-api.ts` 中统一导出 `flora-client.ts`、`flora-auth.ts`、`flora-jobs.ts`、`flora-cloud.ts` 的全部方法与类型：

```ts
export type FloraProjectSummary = {
    id: string;
    name: string;
    version: number;
    created_at: string;
    updated_at: string;
};

export type FloraCanvasDataSnapshot = {
    nodes?: CanvasNodeData[];
    connections?: CanvasConnection[];
    chatSessions?: CanvasAssistantSession[];
    activeChatId?: string | null;
    backgroundMode?: CanvasBackgroundMode;
    showImageInfo?: boolean;
    viewport?: ViewportTransform;
};

export type FloraProjectDetail = FloraProjectSummary & {
    canvas_data: FloraCanvasDataSnapshot;
};

export type FloraUserAsset = {
    id: string;
    project_id: string | null;
    job_id: string | null;
    source: "generated" | "uploaded";
    object_key: string;
    signed_url: string | null;
    mime_type: string;
    created_at: string;
};

export type FloraJobHistoryItem = {
    job_id: string;
    status: FloraJobStatus;
    capability: string;
    model: string;
    prompt: string;
    cost_credits: number;
    result_urls: string[];
    error_code: string | null;
    message: string | null;
    created_at: string;
    updated_at: string;
    finished_at: string | null;
};
```

提供以下导出函数（全部走 `floraRequest<T>`，支持注入可选 `fetchImpl` 便于 `bun test` 单测）：
- `listCloudProjects(options?: { fetchImpl?: typeof fetch }): Promise<FloraProjectSummary[]>` → `GET /api/v1/projects`
- `getCloudProject(projectId: string, options?: { fetchImpl?: typeof fetch }): Promise<FloraProjectDetail>` → `GET /api/v1/projects/${encodeURIComponent(projectId)}`
- `createCloudProject(input: { name: string; canvas_data?: FloraCanvasDataSnapshot }, options?: { fetchImpl?: typeof fetch }): Promise<FloraProjectDetail>` → `POST /api/v1/projects`
- `updateCloudProject(projectId: string, input: { name?: string; canvas_data?: FloraCanvasDataSnapshot }, options?: { fetchImpl?: typeof fetch }): Promise<FloraProjectDetail>` → `PUT /api/v1/projects/${encodeURIComponent(projectId)}`
- `deleteCloudProject(projectId: string, options?: { fetchImpl?: typeof fetch }): Promise<{ id: string; deleted: boolean }>` → `DELETE /api/v1/projects/${encodeURIComponent(projectId)}`
- `listCloudAssets(options?: { limit?: number; fetchImpl?: typeof fetch }): Promise<FloraUserAsset[]>` → `GET /api/v1/assets?limit=${limit}`
- `deleteCloudAsset(assetId: string, options?: { fetchImpl?: typeof fetch }): Promise<{ id: string; deleted: boolean }>` → `DELETE /api/v1/assets/${encodeURIComponent(assetId)}`
- `listFloraJobHistory(options?: { limit?: number; fetchImpl?: typeof fetch }): Promise<FloraJobHistoryItem[]>` → `GET /api/v1/jobs/history?limit=${limit}`
- 转换辅助函数：
  - `toCanvasDataSnapshot(project: CanvasProject): FloraCanvasDataSnapshot`（提取 `nodes`、`connections`、`chatSessions`、`activeChatId`、`backgroundMode`、`showImageInfo`、`viewport`）
  - `fromCloudProjectDetail(detail: FloraProjectDetail): CanvasProject`（将后端 `FloraProjectDetail` 转为前端 `CanvasProject`）
  - `fromCloudAssetToStoreAsset(item: FloraUserAsset): ImageAsset`（将后端 `FloraUserAsset` 转为前端 `ImageAsset`，`id = "cloud:" + item.id`，`dataUrl = item.signed_url || ""`）

### 2. 画布项目云同步（`use-canvas-store.ts`、`/canvas`、`/canvas/:id`）

- 在 `web/src/stores/canvas/use-canvas-store.ts` 新增云端同步状态与动作（不破坏现有本地方法签名，保持所有现有单测兼容）：
  - `cloudSyncStatus: "local" | "syncing" | "synced" | "error"`
  - `cloudVersions: Record<string, number>`（记录每个云端项目的 `version`）
  - `syncProjectsFromCloud(fetchImpl?)`：若当前无 `getStoredFloraToken()`，置 `cloudSyncStatus: "local"` 并直接返回；若有 token，调 `listCloudProjects` 获取项目摘要列表，与本地 `projects` 合并（云端已有的项目更新标题与时间；若本地还没有该项目的 `nodes` 详情，先放入带空 `nodes` 的占位项目，打开 `/canvas/:id` 时再调 `loadCloudProject(id)` 拉取完整 `canvas_data`）。
  - `createCloudProjectAndOpen(title, fetchImpl?)`：若已登录，调 `createCloudProject({ name: title, canvas_data: ... })`，写入 `projects` 并返回云端 UUID；若未登录，走原本地 `createProject(title)`。
  - `loadCloudProject(projectId, fetchImpl?)`：若已登录且 `projectId` 为 UUID 格式，调 `getCloudProject(projectId)` 拉取最新 `canvas_data` 写入 `useCanvasStore` 并更新 `cloudVersions[projectId] = detail.version`。
  - `saveProjectToCloud(projectId, fetchImpl?)`：若已登录，取当前项目快照调 `updateCloudProject(projectId, { name: project.title, canvas_data: toCanvasDataSnapshot(project) })`（若该项目是本地旧的 nanoid 非 UUID，则自动调 `createCloudProject` 上云并替换本地 id），成功后置 `cloudSyncStatus: "synced"` 并更新 `cloudVersions`。
- 在 `web/src/pages/canvas/index.tsx` 与 `web/src/pages/canvas/project.tsx`：
  - 当用户已登录（`useAuthStore((s) => Boolean(s.token))`）时：
    - `/canvas` 列表页自动执行 `syncProjectsFromCloud()`，新建项目优先创建云端项目，删除项目同步调用 `deleteCloudProject`；顶部展示云同步状态标签（如「已开启账号云同步」+「刷新云端」按钮）；
    - `/canvas/:id` 画布页挂载时自动调用 `loadCloudProject(id)`，当画布内容或标题变更时自动防抖（1000ms）同步到云端，并在画布顶栏展示云同步状态徽章（`已同步 v{version}` / `同步中...` / 点击立即保存）；
  - 当用户未登录时：
    - 显示「本地存储（登录后可跨设备云同步）」，不伪装成已上云。

### 3. 云端资产与生成历史同步（`/assets` 与 `/image`）

- 在 `web/src/pages/assets/index.tsx`：
  - 当用户已登录时，页面加载及点击「同步云端素材」时调用 `listCloudAssets()`，将返回的云端生成资产（带最新 `signed_url`）合并进资产列表展示（标记来源 `cloud / generated`），删除云端资产时同步调用 `deleteCloudAsset`。
- 在 `web/src/pages/image/index.tsx`：
  - 当用户已登录时，页面加载及每次生图完成后调用 `listFloraJobHistory({ limit: 50 })`，将云端历史任务（含 `succeeded` 的 `result_urls` 签名图片与 `dead` 的友好错误）合并展示在生成历史列表中，使得**换一个全新浏览器登录同一账号，打开 `/image`、`/assets`、`/canvas` 都能立即看到之前生成的图片、任务记录与画布项目**。

---

## 文档同步要求

1. **新建 `docs/engineering/feature-briefs/2026-10-24-wp10-cloud-persistence.md`**：将本文件全文落仓。
2. **新建 `docs/engineering/data-model/projects.md`**：写入 `canvas_project`（7 列）与 `user_asset`（8 列）完整字段级定义、外键与索引说明。
3. **更新 `docs/engineering/data-model/README.md`**：
   - 将表清单中「画布 / 素材 / 生成记录」一行更新为：`canvas_project` / `user_asset` | C 端数据 | WP10 已落地（`V6`） | [projects.md](./projects.md) | 画布快照与用户素材，绑定 `user_id`。
4. **更新 `docs/engineering/data-model/errors.md`**：
   - 在注册表中新增 `PROJECT_NOT_FOUND` 与 `ASSET_NOT_FOUND` 两行说明。
5. **更新 `docs/engineering/api/gateway-contract.md`**：
   - 新增「Projects（`/api/v1/projects`）」、「Assets（`/api/v1/assets`）」与「Job History（`/api/v1/jobs/history`）」接口契约章节。
6. **更新 `docs/engineering/feature-briefs/README.md`**：
   - 将 WP7 状态更新为 `已实现`；
   - 新增 WP10 行：`[2026-10-24-wp10-cloud-persistence.md](./2026-10-24-wp10-cloud-persistence.md) | WP10 / M3 | C 端账号级云端持久化（项目 + 资产 + 历史 + TOS 虚拟主机域名兼容 + Anytoken 真实模型对齐） | 已实现（待验收）`。
7. **更新 `docs/engineering/runbooks/local-dev.md`**：
   - 增加火山引擎华南区真实 TOS 环境变量配置说明（`FLORA_TOS_REGION=cn-guangzhou`、`FLORA_TOS_ENDPOINT=https://tos-cn-guangzhou.volces.com`、测试桶 `flora-media-test` / 生产桶 `flora-media-prod`，密钥用占位符 `<your-ak>` / `<your-sk>`）；
   - 增加真实 Anytoken 环境变量配置说明（`FLORA_GATEWAY_MODE=http`、`FLORA_GATEWAY_BASE_URL=https://api.anytoken.work/v1`、`FLORA_GATEWAY_GENERATE_PATH=/v1/images/generations`、`FLORA_GATEWAY_API_KEY=<your-image-key>`、`FLORA_GATEWAY_VIDEO_API_KEY=<your-seedance-key>`，以及开通的生图模型 `gpt-image-2`、`gpt-image-2.5-flare`、`gpt-image-2.5-sunburst`、`image2` 与视频模型 `seedance2`、`seedance2.5`、`seedance-mini`、`seedance-fast`）；
   - 增加 WP10 跨会话/换浏览器云端持久化验证步骤（创建项目 → 保存画布节点 → 生图成功 → 调 `GET /api/v1/projects`、`GET /api/v1/assets`、`GET /api/v1/jobs/history` 验证数据与虚拟主机签名 URL）。

---

## 验收自检清单（PR Review 逐条对照，共 17 项）

- [ ] 1. `docs/engineering/feature-briefs/2026-10-24-wp10-cloud-persistence.md` 与 `docs/engineering/data-model/projects.md` 已新建落仓，`data-model/README.md`、`data-model/errors.md`、`api/gateway-contract.md`、`feature-briefs/README.md`、`runbooks/local-dev.md` 已同步更新。
- [ ] 2. **已有迁移文件零改动**：`V1`、`V2`、`V3`、`V4`、`V5` SQL 文件 0 行改动。
- [ ] 3. **新建 `V6__create_cloud_persistence.sql`**：`canvas_project` 严格为 7 列（`id, user_id, name, canvas_data, version, created_at, updated_at` + `idx_canvas_project_user_updated`），`user_asset` 严格为 8 列（`id, user_id, project_id, job_id, source, object_key, mime_type, created_at` + `uk_user_asset_job_object` + `idx_user_asset_user_created`），并灌入 `PROJECT_NOT_FOUND` 与 `ASSET_NOT_FOUND`（含 `error.*` 别名）中英文种子词条。
- [ ] 4. **TOS 虚拟主机风格（`api`）**：`TosUrlSigner` 对 `127.0.0.1` / `localhost` / `[::1]` 保持 Path-Style（现有确定性签名向量测试 100% 原样通过）；对真实域名（如 `https://tos-cn-guangzhou.volces.com`，bucket `flora-media-test`）生成 `https://flora-media-test.tos-cn-guangzhou.volces.com/<key>?...` 且按 `host:flora-media-test.tos-cn-guangzhou.volces.com` 与 `canonicalUri:/<key>` 签名。
- [ ] 5. **TOS 虚拟主机风格（`worker`）**：`TosObjectStorageAdapter` 对回环地址保持 Path-Style，对真实域名（如 `https://tos-cn-guangzhou.volces.com`）使用 `https://<bucket>.tos-cn-guangzhou.volces.com/<key>` 发起 V4 Header 签名 `PUT`；`api` 与 `worker` 均新增对应单测并通过。
- [ ] 6. **Anytoken 真实协议与模型对齐（`worker` + `web`）**：`AnytokenProperties` 支持可选 `videoApiKey`（保留 5 参数兼容构造器 + `@ConstructorBinding`）；`AnytokenGatewayAdapter` 支持 `/v1` 路径去重、OpenAI `/v1/images/generations` 顶层字段输出、`data[].url` 临时图片下载写 TOS；前端 `FLORA_REMOTE_IMAGE_MODELS` 包含 `gpt-image-2`、`gpt-image-2.5-flare`、`gpt-image-2.5-sunburst`、`image2`（默认 `"gpt-image-2"`）并保留既有兼容模型。
- [ ] 7. **瘦 Worker 边界不破**：`worker` 模块编译依赖与包内仍然零 `sa-token`、零 `wallet`、零 `project`/`canvas` 引用。
- [ ] 8. **项目 CRUD 闭环**：`POST /api/v1/projects`、`GET /api/v1/projects`（摘要不含 `canvas_data`）、`GET /api/v1/projects/{id}`（含 `canvas_data`）、`PUT /api/v1/projects/{id}`（`version` 递增 `+1`）、`DELETE /api/v1/projects/{id}` 全部工作正常。
- [ ] 9. **跨用户越权隔离**：用户 B 访问、更新或删除用户 A 的 `project_id` 或 `asset_id` 时，一律返回 HTTP 200 + `ok: false, error_code: "PROJECT_NOT_FOUND"`（或 `"ASSET_NOT_FOUND"`）+ 本地化友好 `message`，不泄露他人资源存在性。
- [ ] 10. **云端资产自动同步与现签 URL**：`GET /api/v1/assets` 自动将当前用户 `succeeded` 任务的 `result_object_keys` 幂等同步进 `user_asset`（重复调用不报错、不产生重复行），且每条资产返回现签的 `signed_url`（数据库只存 `object_key`，不落库预签名 URL）。
- [ ] 11. **生图历史接口**：`GET /api/v1/jobs/history`（及 `GET /api/v1/jobs`）按 `created_at DESC` 返回当前用户任务历史，含 `prompt`、`cost_credits`、`result_urls`（成功任务现签 TOS 读地址）及本地化错误 `message`。
- [ ] 12. **前端 API 封装**：`web/src/services/flora-api.ts`（及 `web/src/services/api/flora-cloud.ts`）提供完整的项目、资产、任务历史类型化函数，并带 `bun test` 单测覆盖。
- [ ] 13. **前端画布、资产与历史云同步**：已登录状态下，`/canvas`、`/canvas/:id`、`/assets`、`/image` 自动对接云端接口，清空本地缓存或换浏览器登录后仍能加载出自己的云端项目、资产与生图历史；未登录时退回本地存储且 UI 如实显示本地状态。
- [ ] 14. **中英双语完备**：后端 `PROJECT_NOT_FOUND` / `ASSET_NOT_FOUND` 支持 `zh` / `en` 解析；前端新增云同步静态文案在 `zh-CN.ts` 与 `en-US.ts` 成对补齐。
- [ ] 15. **后端单测全绿**：根目录执行 `./mvnw test` 全部通过（含新增的 `ProjectServiceTest`、`AssetServiceTest`、`ListUserJobsTest`、`ProjectControllerSliceTest`、TOS 虚拟主机单测、Anytoken OpenAI 协议兼容单测等）。
- [ ] 16. **前端单测与构建全绿**：执行 `cd web && bun test && bun run build` 全部通过（含新增的 `web/tests/flora-cloud.test.ts` 与更新后的 `model-selection.test.ts`）。
- [ ] 17. **Git 与安全规范**：分支名 `feat/wp10-cloud-persistence`，PR 指向 `main`；仓库内绝对不含任何真实火山引擎 AK/SK 或 Anytoken `sk-...` 密钥；`docs/content/**` 与 `CHANGELOG.md` 0 行改动；**不要自行 merge**。

---

## 非目标

- 不做多人实时协同画布（CRDT / WebSocket 多人光标）
- 不做大文件前端直传 TOS 预签名 PUT（阶段 1 重点是画布 JSON 快照云端保存 + 生成结果图片资产与生成历史随账号云端持久化）
- 不做运营后台 Admin 接口或页面（留给最后一个主线包 **WP11 / M4**）
- 不修改 `docs/content/**` 与 `CHANGELOG.md`

---

## 留给实现者的选择（最多拍这 1 项，PR 写明选了哪样）

1. **已登录用户在画布内触发云端保存（`PUT /api/v1/projects/{id}`）的交互方式**：
   - **方式 A（推荐，自动防抖 + 手动按钮双保险）**：画布节点/连线/标题变更后按 `1000ms` 防抖自动调用 `saveProjectToCloud(projectId)`，同时在画布顶栏提供可点击的「云端已同步 `v{version}` / 立即保存」状态按钮；
   - **方式 B**：进入/离开画布与点击顶栏「保存上云」按钮时触发 `saveProjectToCloud(projectId)`，辅以定时自动保存。
   无论选哪种，必须保证换浏览器登录后打开该项目能完整恢复保存过的画布节点与连线。

不要再自行扩大其它开放问题。

---

## 分支 / Commit / PR 指令

```bash
git fetch origin
git checkout main
git pull origin main
git checkout -b feat/wp10-cloud-persistence
git add docs/engineering api worker web
git commit -m "WP10: C端项目与资产云端持久化及TOS/Anytoken真实环境兼容"
git push -u origin feat/wp10-cloud-persistence
```

开 PR → `main`：

- 标题：`WP10: C 端账号级云端持久化（项目 + 资产 + 历史 + TOS 虚拟主机 & Anytoken 兼容）`
- 正文必须含：brief 路径 `docs/engineering/feature-briefs/2026-10-24-wp10-cloud-persistence.md`、做了什么、17 项验收清单逐条自勾、故意没做（无多人协同 / 无 Admin 后台 / 无真实密钥入库）、`./mvnw test` 与 `bun test && bun run build` 结果、本地联调实测记录、实现者选择结论
- **不要自行 merge**。