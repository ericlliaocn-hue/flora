# WP12：Seedance 视频生成全链路接通（`video.generate` + Anytoken `/v1/videos/*` 异步协议 + TOS `.mp4` 归档 + `/video` 与画布云同步）

- **状态**：已实现（待验收）
- **里程碑**：M2/M3 视频生成闭环扩展（Seedance 端到端全栈接入）
- **建议分支**：`feat/wp12-seedance-video-generation`
- **预计 PR 数**：1（同一 PR 内落盘本 brief、更新工程文档，并完成 `api` + `worker` + `web` 实现与测试）
- **前置已合并**：WP0–WP11（PR #1–#14）均已合入 `main`（最新 commit `bc70fab`）

---

## 0. 给实施者（Minimax）的先读指令

动工前**必须**按顺序读完以下文件（以仓内当前内容为准）：

1. `AGENTS.md`
2. `docs/engineering/ARCHITECTURE.md`、`docs/engineering/DEVELOPMENT.md`
3. `docs/engineering/adr/0001-anytoken-not-newapi-fork.md`、`docs/engineering/adr/0004-thin-worker-no-god-classes.md`、`docs/engineering/adr/0005-friendly-errors-vs-raw-upstream.md`
4. `docs/engineering/data-model/README.md`、`docs/engineering/data-model/jobs.md`、`docs/engineering/data-model/projects.md`、`docs/engineering/api/gateway-contract.md`
5. 仓内现有实现：
   - `api/src/main/resources/db/migration/V1__create_users.sql` ～ `V7__create_admin_ops.sql`
   - `api/src/main/java/com/pilipala/api/job/application/SubmitJob.java`、`JobCostPolicy.java`、`GetJobResult.java`、`ListUserJobs.java`
   - `api/src/main/java/com/pilipala/api/project/application/AssetService.java`
   - `worker/src/main/java/com/pilipala/worker/gateway/AnytokenProperties.java`、`AnytokenGatewayAdapter.java`
   - `worker/src/main/java/com/pilipala/worker/storage/MinimalPng.java`、`TosObjectStorageAdapter.java`
   - `worker/src/main/resources/application.yml`
   - `web/src/stores/use-config-store.ts`、`web/src/services/api/pilipala-jobs.ts`、`web/src/services/api/video.ts`、`web/src/services/api/pilipala-cloud.ts`
   - `web/src/pages/video/index.tsx`、`web/src/pages/image/index.tsx`、`web/src/pages/assets/index.tsx`

---

## 1. 背景（一句话）

WP10 虽在 `worker` 的 `AnytokenProperties` 中预留了 `videoApiKey`（`PILIPALA_GATEWAY_VIDEO_API_KEY`），但后端 `AnytokenGatewayAdapter` 仍把所有任务发往 `/v1/images/generations` 并存为 `.png`，且前端 `/video` 页面与画布视频节点尚未接入 pilipala 后端 `/api/v1/jobs/submit`；本工作包将 **Anytoken Seedance 真实视频协议（`POST /v1/videos/generations` → `GET /v1/videos/{id}` → `GET /v1/videos/{id}/content`）**、**火山 TOS `video/mp4` 存取**、**`user_asset` 视频自动归档**、**`model_display_config` 4 个 Seedance 模型种子**以及**前端 `/video` 页与画布视频生成 + 云历史同步**一次性全栈打通。

---

## 2. 范围（必须做）

### Part A：`api` 侧——Seedance 模型种子迁移、结果类型识别与视频素材归档

#### A1. 新增 Flyway 迁移 `api/src/main/resources/db/migration/V8__seed_seedance_video_models.sql`

> **严禁修改 `V1`–`V7` 任何已有迁移文件**。新增 `V8__seed_seedance_video_models.sql`，向 `model_display_config` 幂等插入 4 个 Anytoken Seedance 真实视频模型：

```sql
INSERT INTO model_display_config (model, capability, display_name, enabled, sort_order) VALUES
    ('seedance2', 'video.generate', 'Seedance 2', true, 50),
    ('seedance2.5', 'video.generate', 'Seedance 2.5', true, 60),
    ('seedance-fast', 'video.generate', 'Seedance Fast', true, 70),
    ('seedance-mini', 'video.generate', 'Seedance Mini', true, 80)
ON CONFLICT (model) DO NOTHING;
```

#### A2. `GetJobResult` 根据任务能力或后缀返回 `"video"` 输出类型

修改 `api/src/main/java/com/pilipala/api/job/application/GetJobResult.java`：
- 当前第 74 行写死了 `new JobOutput("image", objectKey, tosUrlSigner.signGetUrl(objectKey))`。
- 改为根据 `job.capability()` 与 `objectKey` 判断输出类型：
  ```java
  String outputType = isVideoJob(job.capability(), objectKey) ? "video" : "image";
  outputs.add(new JobOutput(outputType, objectKey, tosUrlSigner.signGetUrl(objectKey)));
  ```
  其中 `isVideoJob(String capability, String objectKey)`：当 `(capability != null && capability.startsWith("video")) || (objectKey != null && objectKey.toLowerCase(Locale.ROOT).endsWith(".mp4"))` 时返回 `true`。

#### A3. `AssetService` 自动归档视频任务产物为 `video/mp4`

修改 `api/src/main/java/com/pilipala/api/project/application/AssetService.java`：
- 当前 `archiveSucceededJobs` 第 69 行写死了 `"image/png"`：
  `assets.insertGeneratedIfAbsent(userId, job.id(), objectKey, "image/png");`
- 改为自动推导 `mimeType`：
  - 若 `(job.capability() != null && job.capability().startsWith("video")) || objectKey.toLowerCase(Locale.ROOT).endsWith(".mp4")`，传入 `"video/mp4"`；
  - 否则传入 `"image/png"`。

---

### Part B：`worker` 侧——Anytoken Seedance 视频生成三步协议与 `video/mp4` 入桶

#### B1. 新建 `worker/src/main/java/com/pilipala/worker/storage/MinimalMp4.java`

与 `MinimalPng.BYTES` 对齐，提供最小合法 MP4（ISO BMFF `ftypisom` 头部字节数组 `MinimalMp4.BYTES`），用于 `stub` 模式及单测回退：

```java
package art.pilipala.worker.storage;

/**
 * Minimal 32-byte ISO Base Media File Format (MP4 ftypisom) box for stub/test video outputs.
 */
public final class MinimalMp4 {

    public static final byte[] BYTES = new byte[] {
        0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70,
        0x69, 0x73, 0x6f, 0x6d, 0x00, 0x00, 0x02, 0x00,
        0x69, 0x73, 0x6f, 0x6d, 0x69, 0x73, 0x6f, 0x32,
        0x6d, 0x70, 0x34, 0x31, 0x00, 0x00, 0x00, 0x00
    };

    private MinimalMp4() {
    }
}
```

#### B2. 扩展 `AnytokenProperties`（保持已有构造器 100% 向后兼容）

修改 `worker/src/main/java/com/pilipala/worker/gateway/AnytokenProperties.java` 与 `worker/src/main/resources/application.yml`：
1. 在 `AnytokenProperties` 增加视频端点与轮询配置字段（给足默认值）：
   - `videoGeneratePath`：默认 `"/v1/videos/generations"`
   - `videoStatusPathPrefix`：默认 `"/v1/videos"`
   - `videoPollIntervalMs`：默认 `2000L`（`<= 0` 时兜底 `2000L`）
   - `videoMaxPollAttempts`：默认 `90`（`<= 0` 时兜底 `90`）
2. **必须保留现有的 5 参数构造器与 6 参数构造器**，并在新的全参主构造器上标 `@ConstructorBinding`（确保现有 `AnytokenGatewayAdapterTest` 与 `AnytokenPropertiesBindingTest` 零报错通过）。
3. `worker/src/main/resources/application.yml` 的 `pilipala.gateway.anytoken` 下补充：
   ```yaml
   video-generate-path: ${PILIPALA_GATEWAY_VIDEO_GENERATE_PATH:/v1/videos/generations}
   video-status-path-prefix: ${PILIPALA_GATEWAY_VIDEO_STATUS_PATH_PREFIX:/v1/videos}
   video-poll-interval-ms: ${PILIPALA_GATEWAY_VIDEO_POLL_INTERVAL_MS:2000}
   video-max-poll-attempts: ${PILIPALA_GATEWAY_VIDEO_MAX_POLL_ATTEMPTS:90}
   ```

#### B3. `AnytokenGatewayAdapter` 支持 `video.generate` 三步异步协议

修改 `worker/src/main/java/com/pilipala/worker/gateway/AnytokenGatewayAdapter.java`：

1. **判断视频能力**：
   - `boolean isVideo = request.capability() != null && request.capability().startsWith("video");`
2. **`stub` 模式分流**：
   - 当 `isVideo == true` 时：
     - 生成对象键：`"anytoken/vid/" + request.jobId() + "/" + request.attempt() + ".mp4"`
     - 调用 `tosStorage.putObject(key, MinimalMp4.BYTES, "video/mp4")`
     - 返回 `GatewayOutcome.success(200, upstreamId, rawBody, List.of(key))`
   - 当 `isVideo == false` 时：保持现有 `.png` + `"image/png"` 行为完全不变。
3. **`http` 模式下的 Seedance 视频三步协议**（当 `isVideo == true` 时）：
   - 使用 `properties.apiKeyForCapability(request.capability())` 自动选用 `PILIPALA_GATEWAY_VIDEO_API_KEY`。
   - **Step 1：提交视频生成任务 `POST /v1/videos/generations`**
     - 目标 URI：`resolveTargetUri(properties.baseUrl(), properties.videoGeneratePath())`（同样自动去重 `/v1`）。
     - 请求头：`Authorization: Bearer <videoApiKey>`、`Content-Type: application/json`、`Idempotency-Key: <jobId>:<attempt>`。
     - 请求体构建 `buildVideoPayload(GatewayGenerateRequest request)`：
       - `model`：若 `request.model()` 为空则默认 `"seedance2"`，否则取 `request.model()`（支持 `seedance2`、`seedance2.5`、`seedance-fast`、`seedance-mini`）。
       - `prompt`：从 `params.get("prompt")` 提取，默认 `""`。
       - `aspect_ratio`：优先取 `params.get("aspect_ratio")`；若未传则根据 `params.get("size")` 映射：
         - `"1280x720"` / `"1920x1080"` / `"1792x1024"` → `"16:9"`
         - `"720x1280"` / `"1080x1920"` / `"1024x1792"` → `"9:16"`
         - `"1024x1024"` → `"1:1"`
         - `"1536x1024"` → `"4:3"`
         - `"1024x1536"` → `"3:4"`
         - `"1792x768"` → `"21:9"`
         - 其他或未传 → 默认 `"16:9"`。
       - `duration`：优先取 `params.get("duration")`，其次取 `params.get("seconds")`（支持 Number 或数字字符串，如 `"5"`），解析为正整数，默认 `5`。
       - `resolution`：取 `params.get("resolution")`，当非空且不为 `"auto"` 时写入顶层 `"resolution"`（如 `"720p"` 或 `"1080p"`）。
       - 可选参考素材透传：若 `params.get("reference_images")` 非空则透传 `reference_images`；若 `params.get("reference_videos")` 非空则透传 `reference_videos`。
     - 若 HTTP 状态码非 2xx：按现有错误分类逻辑（429/5xx 可重试，其余不可重试）返回 `GatewayOutcome.failure(...)`。
     - 解析响应 JSON：
       - 提取任务 ID：`taskId = firstNonBlank(node.path("id").asText(null), node.path("request_id").asText(null), node.path("data").path("id").asText(null), headerRequestId)`。若为空则返回不可重试失败 `"GATEWAY_INVALID_RESPONSE"`。
       - 提取初始状态：`status = extractVideoStatus(node)`（优先 `node.path("status").asText(null)`，其次 `node.path("data").path("status").asText(null)`）。
   - **Step 2：轮询任务状态 `GET /v1/videos/{taskId}`**
     - 定义终态集合（大小写不敏感）：
       - 成功终态：`completed`、`succeeded`、`success`
       - 失败终态：`failed`、`cancelled`、`error`
     - 若 Step 1 返回的 `status` 尚未进入终态，则循环最多 `properties.videoMaxPollAttempts()` 次：
       - 每次先 `Thread.sleep(properties.videoPollIntervalMs())`；
       - 请求 `GET resolveTargetUri(properties.baseUrl(), properties.videoStatusPathPrefix() + "/" + URLEncoder.encode(taskId, StandardCharsets.UTF_8))`，带 `Authorization: Bearer <videoApiKey>`；
       - 若返回 429 或 5xx：返回可重试失败；若返回其他 4xx：返回不可重试失败；
       - 更新 `lastRawBody = pollResponse.body()`，`status = extractVideoStatus(pollNode)`；
       - 若进入终态则跳出循环。
     - 若跳出后属于失败终态（`failed` / `cancelled` / `error`）：返回不可重试失败 `GatewayOutcome.failure(extractErrorCode(lastNode, "UPSTREAM_VIDEO_FAILED"), lastStatus, taskId, lastRawBody, false)`。
     - 若达到最大轮询次数仍未进入成功终态：返回可重试失败 `GatewayOutcome.failure("GATEWAY_TIMEOUT", lastStatus, taskId, lastRawBody, true)`。
   - **Step 3：下载视频字节并上传火山 TOS**
     - 优先请求 `GET resolveTargetUri(properties.baseUrl(), properties.videoStatusPathPrefix() + "/" + URLEncoder.encode(taskId, StandardCharsets.UTF_8) + "/content")`，带 `Authorization: Bearer <videoApiKey>`：
       - 若返回 2xx 且 `body.length > 0`，直接使用该 `byte[]` 作为 `mp4Bytes`；
     - 回退提取直链：若 `/content` 未返回字节或最后 JSON 中已含视频直链（检查 `url`、`video_url`、`data.url`、`data[0].url`、`output.video_url`），则下载该 URL；若在测试环境回退失败则兜底 `MinimalMp4.BYTES`。
     - 对象键：`String key = "anytoken/vid/" + request.jobId() + "/" + request.attempt() + ".mp4";`
     - 调用 `tosStorage.putObject(key, mp4Bytes, "video/mp4")`，返回 `GatewayOutcome.success(200, taskId, lastRawBody, List.of(key))`。

---

### Part C：`web` 侧——Seedance 模型注册、`/video` 与画布接入 pilipala 任务队列、视频云历史与素材展示

#### C1. `web/src/stores/use-config-store.ts` 注册 4 个 Seedance 远端视频模型

1. 新增导出常量与辅助函数：
   ```ts
   export const PILIPALA_REMOTE_VIDEO_MODELS: ChannelModel[] = [
       { name: "seedance2", capability: "video" },
       { name: "seedance2.5", capability: "video" },
       { name: "seedance-fast", capability: "video" },
       { name: "seedance-mini", capability: "video" },
   ];

   export function isRemoteVideoModel(model: string | undefined): boolean {
       const trimmed = model?.trim();
       if (!trimmed) return false;
       return PILIPALA_REMOTE_VIDEO_MODELS.some((item) => item.name === trimmed);
   }

   export function remoteVideoModel(preferred?: string): string {
       const trimmed = preferred?.trim();
       if (trimmed && isRemoteVideoModel(trimmed)) {
           return trimmed;
       }
       return PILIPALA_REMOTE_VIDEO_MODELS[0].name;
   }
   ```
2. 在 `VIDEO_KEYWORDS` 数组中加入 `"seedance"`（确保 `inferModelCapability("seedance2")` 返回 `"video"`）。
3. 将 `defaultConfig.videoModel` 默认值设为 `"seedance2"`。
4. 在 `selectableModelsByCapability(config, capability)` 中：
   - 当 `config.channelMode === "remote"` 时：
     - 若 `!capability || capability === "image"`，包含 `PILIPALA_REMOTE_IMAGE_MODELS`；
     - 若 `!capability || capability === "video"`，将 `PILIPALA_REMOTE_VIDEO_MODELS.map((m) => m.name)` 排在视频模型列表最前面。
5. 在 `resolveModelForCapability(config, preferred, capability)` 中：
   - **保持原有的 `decodeChannelModel(preferred)` 分支在最前面不变**（确保 `web/tests/model-selection.test.ts` 现有 11 条单测 100% 原样通过！）；
   - 当 `config.channelMode === "remote"` 且 `capability === "video"` 且未命中 `decodeChannelModel` 时：若 `isRemoteVideoModel(preferred)` 返回 `preferred!.trim()`，否则返回 `remoteVideoModel(config.videoModel)`。
6. 在 `isAiConfigReady(config, model)` 中：
   - 当 `!decodeChannelModel(trimmed)` 且 `config.channelMode === "remote"` 且 `(isRemoteImageModel(trimmed) || isRemoteVideoModel(trimmed))` 时，直接返回 `true`（无需本地直连 `apiKey`）。

#### C2. `web/src/services/api/pilipala-jobs.ts` 与 `web/src/services/api/video.ts` 接入 `video.generate`

1. 在 `web/src/services/api/pilipala-jobs.ts` 中新增：
   - `export function usesPilipalaManagedVideoChannel(config: AiConfig): boolean`：
     当 `config.channelMode === "remote" && !decodeChannelModel(config.model || config.videoModel)` 时返回 `true`。
   - `export function resolvePilipalaVideoModel(config: AiConfig): string`：
     提取 `modelOptionName(config.model || config.videoModel || "seedance2")`；若属于 `PILIPALA_REMOTE_VIDEO_MODELS` 则返回该值，否则默认返回 `"seedance2"`。
   - `export async function submitPilipalaVideoJob(config: AiConfig, prompt: string, options?: { size?: string; seconds?: string; resolution?: string; aspectRatio?: string }): Promise<{ jobId: string; model: string }>`：
     调用 `submitPilipalaJob` 发送：
     ```ts
     {
       capability: "video.generate",
       model,
       params: {
         prompt,
         size: options?.size ?? config.videoSize,
         seconds: options?.seconds ?? config.videoSeconds,
         duration: Number(options?.seconds ?? config.videoSeconds) || 5,
         resolution: options?.resolution ?? config.videoResolution,
         ...(options?.aspectRatio ? { aspect_ratio: options.aspectRatio } : {}),
       },
     }
     ```
2. 在 `web/src/services/api/video.ts` 中接入 pilipala 托管视频通道：
   - 将 `VideoGenerationTask["provider"]` 类型扩展为 `"openai" | "gemini" | "plugin" | "pilipala"`。
   - 在 `createVideoGenerationTask(config, prompt, references, options)` 开头：
     若 `usesPilipalaManagedVideoChannel(config)` 为 `true`，调用 `submitPilipalaVideoJob(config, prompt, { size: options?.size ?? config.videoSize, seconds: options?.seconds ?? config.videoSeconds, resolution: options?.resolution ?? config.videoResolution })`，返回 `{ id: submitted.jobId, provider: "pilipala", model: submitted.model }`。
   - 在 `pollVideoGenerationTask(config, task, options)` 开头：
     若 `task.provider === "pilipala"`：
     - 调用 `getPilipalaJob(task.id)`；
     - 若 `job.status === "succeeded"`，再调用 `getPilipalaJobResult(task.id)`，取 `result.outputs[0]?.url`，返回 `{ status: "completed", progress: 100, result: { url, mimeType: "video/mp4" } }`；
     - 若 `job.status === "failed" || job.status === "dead"`，返回 `{ status: "failed", error: job.message || job.error_code || "Video generation failed" }`；
     - 若 `job.status === "running"`，返回 `{ status: "processing", progress: 50 }`；
     - 否则返回 `{ status: "queued", progress: 15 }`。
   - 这样 `/video` 页面（`web/src/pages/video/index.tsx`）和画布视频节点（`web/src/pages/canvas/project.tsx`、`use-plugin-host.tsx`）无需重复造轮子即可同时走通 pilipala 后端队列！

#### C3. `web/src/pages/video/index.tsx` 与 `web/src/pages/image/index.tsx` 云端历史同步及 `pilipala-cloud.ts` 视频素材映射

1. 修改 `web/src/services/api/pilipala-cloud.ts` 的 `fromCloudAssetToStoreAsset(item: PilipalaCloudAsset)`：
   - 当 `item.mime_type?.startsWith("video/") || item.object_key.toLowerCase().endsWith(".mp4")` 时，返回 `VideoAsset`：
     ```ts
     return {
         id: `cloud_asset_${item.id}`,
         kind: "video",
         name: fileName,
         createdAt,
         updatedAt: createdAt,
         source: item.source === "upload" ? "upload" : "generated",
         tags: ["cloud", item.source],
         data: {
             url: item.url,
             width: 1280,
             height: 720,
             duration: 5,
             bytes: 0,
             mimeType: item.mime_type || "video/mp4",
         },
     };
     ```
2. 修改 `web/src/pages/image/index.tsx` 的 `syncCloudHistory`：
   - 过滤 `items.filter((item) => item.capability === "image.generate")`，避免把 `video.generate` 任务混入生图历史。
3. 修改 `web/src/pages/video/index.tsx`：
   - 与 `/image` 页一致，当用户已登录（`hasPilipalaToken()`）时，挂载及生成完成后调用 `listPilipalaJobHistory({ limit: 50 })`，筛选 `item.capability === "video.generate"` 的任务并映射为 `VideoTaskLog`（`succeeded` → `"completed"` + `resultUrl: item.result_urls[0]`，`failed`/`dead` → `"failed"`，`running` → `"processing"`，`queued` → `"queued"`），实现跨浏览器视频生成历史云同步。

---

### Part D：工程文档同步更新

1. 将本 brief 完整落盘到 `docs/engineering/feature-briefs/2026-10-24-wp12-seedance-video-generation.md`，并更新 `docs/engineering/feature-briefs/README.md` 增加 WP12 行（状态「已实现」），同时将 WP11 状态标为「已实现」。
2. 更新 `docs/engineering/data-model/README.md`：在 `model_display_config` 说明中补充 `V8__seed_seedance_video_models.sql` 预置的 4 个 Seedance 视频模型（`seedance2`、`seedance2.5`、`seedance-fast`、`seedance-mini`）。
3. 更新 `docs/engineering/api/gateway-contract.md`：补充 `video.generate` 任务提交示例、`GetJobResult` 对视频任务返回 `type: "video"`、以及 Worker 对接 Anytoken `/v1/videos/generations` → `/v1/videos/{id}` → `/v1/videos/{id}/content` 的三步协议说明。

---

## 3. 非范围（明确不做，写一行算违规）

1. **严禁修改 `V1`–`V7` 任何已有 Flyway SQL 文件**（新种子必须走 `V8__seed_seedance_video_models.sql`）。
2. **严禁向 `worker/` 引入 `sa-token`、钱包或画布业务依赖**（遵循 ADR 0004 瘦 worker 边界）。
3. **严禁向 C 端 `/api/v1/jobs/**` 暴露 `provider_task.raw_status` 或 `raw_body`**（遵循 ADR 0005）。
4. **严禁触碰 `docs/content/**` 与根 `CHANGELOG.md`**。
5. **严禁将任何真实密钥（`sk-*`、`AKLT*`）写入仓库受跟踪文件**。

---

## 4. 验收标准（Reviewer 逐项核对清单）

1. [ ] **文档落盘**：`docs/engineering/feature-briefs/2026-10-24-wp12-seedance-video-generation.md` 已创建，`feature-briefs/README.md`、`data-model/README.md`、`api/gateway-contract.md` 已同步更新。
2. [ ] **迁移与种子**：`V1`–`V7` 0 行改动；新增 `V8__seed_seedance_video_models.sql` 幂等插入 `seedance2`、`seedance2.5`、`seedance-fast`、`seedance-mini` 四条 `video.generate` 模型配置。
3. [ ] **`api` 视频结果与素材归档**：
   - `GetJobResult` 对 `video.generate`（或 `.mp4`）产物返回 `type = "video"`。
   - `AssetService` 对 `video.generate`（或 `.mp4`）产物归档 `mime_type = "video/mp4"`。
4. [ ] **`worker` Seedance 三步协议与 `.mp4` 入桶**：
   - `stub` 模式下 `video.generate` 任务写入 `"anytoken/vid/{jobId}/{attempt}.mp4"`（`contentType = "video/mp4"`）。
   - `http` 模式下 `video.generate` 任务使用 `PILIPALA_GATEWAY_VIDEO_API_KEY` 调用 `POST /v1/videos/generations` → 轮询 `GET /v1/videos/{taskId}` → 下载 `GET /v1/videos/{taskId}/content`（支持 URL 直链回退）并以 `video/mp4` 写入火山 TOS。
   - 单测覆盖：Mock HTTP Server 实测 `POST /v1/videos/generations`（含 `aspect_ratio`、`duration`、`resolution` 参数映射）、`GET /v1/videos/{id}` 状态轮询（`processing` → `completed` 以及 `failed` 分支）、`GET /v1/videos/{id}/content` 二进制下载入桶。
5. [ ] **前端 Seedance 模型与 `/video` + 画布云链路**：
   - `use-config-store.ts` 默认 `videoModel = "seedance2"`，远端模式下可选 `seedance2`、`seedance2.5`、`seedance-fast`、`seedance-mini`，且原 `model-selection.test.ts` 全绿。
   - `video.ts` 在远端托管模式下通过 `/api/v1/jobs/submit`（`capability: "video.generate"`）提交并轮询 `/api/v1/jobs/{id}` + `/api/v1/jobs/{id}/result`。
   - `pilipala-cloud.ts` 将 `video/mp4` 云端素材映射为 `kind: "video"`，`/video` 页支持云端视频任务历史同步。
   - 新增前端单测验证 `usesPilipalaManagedVideoChannel`、`createVideoGenerationTask` / `pollVideoGenerationTask` 的 `pilipala` provider 路径与 `fromCloudAssetToStoreAsset` 视频分支。
6. [ ] **全量测试与构建全绿**：
   - `./mvnw test` 全绿。
   - `cd web && bun test` 全绿。
   - `cd web && bun run build` 构建成功。
7. [ ] **红线守卫**：`docs/content/**` 与 `CHANGELOG.md` 0 改动；全仓 0 真实密钥；只提交 PR（`feat/wp12-seedance-video-generation` → `main`），**不要自行 merge**。