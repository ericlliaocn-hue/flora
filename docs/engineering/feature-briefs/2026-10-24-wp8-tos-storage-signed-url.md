# Feature brief：WP8 火山引擎 TOS 对象存储与短期签名读 URL

- 日期：2026-10-24
- 作者：pilipala engineering
- 对应 WP / 里程碑：WP8 / M2（火山 TOS 结果落地与签名读地址）
- 状态：已确认（待实现）

把本文件从「# Feature brief」起到文末 **整份粘贴** 给 Minimax（上方面包屑硬约束一并保留）。不要摘要。

本任务落地 **pilipala 火山引擎 TOS（Volcengine TOS）对象存储与短期预签名读 URL 闭环**：在 `worker` 模块新增 `art.pilipala.worker.storage` 包（唯一 TOS 存储实现，支持本地零密钥 `stub` 落盘模式与 `http` V4 签名 `PUT` 上传模式），在任务执行成功时把生成结果字节写入 TOS 并只在 `job.result_object_keys` 保存对象键；在 `api` 模块新增 `art.pilipala.api.storage` 包（`TosUrlSigner`），在 `GET /api/v1/jobs/{jobId}/result` 为成功任务的每个 `object_key` 签发短期有效（默认 900 秒）的 `TOS4-HMAC-SHA256` 预签名读 URL。**严守 DEVELOPMENT.md 与 ADR 0003 / 0004：不做多云存储抽象、不把 TOS 当数据库（本 WP 无新增 Flyway 迁移）、不在 RabbitMQ 消息或 Postgres 表里塞图片字节或长久裸 URL、不改前端 `web/`。**

## 背景

WP0–WP6 已合入 `main`（当前最新为 WP6 `f9c2a7e`）：

- `api` 已具备认证闸门（WP3 `V1`）、Job 四表与 Outbox 投递（WP4 `V2`）、`provider_task` 原文表（WP5 `V3`）、预付费钱包三表与 `freeze` / `confirm` / `release` 闭环（WP6 `V4`）。
- `worker` 已实现消费 RabbitMQ `pilipala.jobs.execute`、抢 Redis 短锁、调唯一 `AnytokenGatewayAdapter` 并推进 `job` 到终态。
- 但 WP4–WP6 刻意推迟了 TOS：
  1. `worker` 的 `AnytokenGatewayAdapter` 目前只合成字符串 `"anytoken/img/<job_id>/<attempt>.png"`，并未向对象存储写入结果字节；
  2. `api` 的 `GetJobResult` 目前写死 `new JobOutput("image", item.asText(), null)`，导致 `GET /api/v1/jobs/{jobId}/result` 返回的 `outputs[].url` 恒为 `null`。
- 按「先做完后端再接前端」顺序，先完成本 WP（WP8 火山 TOS）与下一包（WP9 `i18n_message`），再推进前端接入（WP7），这样前端联调时 `/api/v1/jobs/{id}/result` 已有真实可用的签名 `url`。

[DEVELOPMENT.md](../DEVELOPMENT.md) WP8：

> - **里程碑**：M2
> - **做什么**：生成结果与必要原图/输出对象进 TOS；API 给前端短期可访问地址；worker 只传对象键，不在 MQ 里塞文件。
> - **不做**：自建多云抽象、把 TOS 当数据库。
> - **完成**：成功任务的结果能用 TOS 键取回。

## 必须遵守

- [TECH_STACK.md](../TECH_STACK.md)：对象存储唯一选型为 **火山引擎 TOS（Volcengine TOS）**；Java 21 + Spring Boot 3；**禁止自建多云抽象（不要建 `S3Storage` / `OssStorage` / `MultiCloudStorageRouter`）**
- [ADR 0001](../adr/0001-anytoken-not-newapi-fork.md)：模型调用仍只走唯一 `AnytokenGatewayAdapter`；对象存储只存生成结果字节，不碰模型路由与额度
- [ADR 0003](../adr/0003-rabbitmq-first-queue-agnostic-jobs.md)：RabbitMQ 消息体仍然只有 `job_id`（及传输层 `event_id`），**严禁往 MQ 消息或 Postgres `job` 表塞图片字节 / Base64 大文本 / 预签名 URL**；数据库 `job.result_object_keys` 只存对象键数组（如 `["anytoken/img/<job_id>/1.png"]`）
- [ADR 0004](../adr/0004-thin-worker-no-god-classes.md)：`worker` 保持瘦进程；TOS `PUT` 上传必须在 `ExecuteJob` 的**数据库事务外**执行（即在 `claim` 短事务之后、`complete` 短事务之前，与 `AnytokenGateway` 同处无事务阶段），禁止拖长数据库连接；`worker` 依然零 `sa-token`、零 `wallet`/`billing` 依赖
- [ADR 0005](../adr/0005-friendly-errors-vs-raw-upstream.md)：TOS 上传失败在 `worker` 侧归一为已登记的 `PROVIDER_UNAVAILABLE`（`retryable = true`），详细错误只进 `provider_task.raw_body` 与日志；**严禁把 TOS `access_key` / `secret_key` / `Authorization` 头写进 `provider_task.raw_body`、日志或 C 端响应**
- [data-model/README.md](../data-model/README.md) & [data-model/jobs.md](../data-model/jobs.md)：TOS 只存字节，不把对象当行存储；**本 WP 不新增数据库表，严禁修改 `V1` / `V2` / `V3` / `V4` 迁移文件，也不要新增 `V5`（`V5` 留给 WP9 `i18n_message`）**
- [api/gateway-contract.md](../api/gateway-contract.md)：`GET /api/v1/jobs/{jobId}/result` 在 `status == "succeeded"` 时，`outputs[]` 每项返回 `{"type": "image", "object_key": "<key>", "url": "<短期签名读地址>"}`；非 `succeeded` 时 `outputs` 仍为空数组 `[]`
- [CONTRIBUTING.md](../CONTRIBUTING.md)：只做 brief 范围；不改 `web/`、`docs/content/**` 与 `CHANGELOG.md`

## 本 WP 拍板（已决定，禁止再猜）

| 项 | 锁定值 |
| --- | --- |
| 数据库迁移 | **无新增迁移**。`job.result_object_keys` 已在 `V2` 建好。**严禁修改 `V1`–`V4`，严禁在 WP8 创建 `V5`** |
| SDK / HTTP 实现选型 | 使用 **JDK 21 标准库**（`java.net.http.HttpClient` + `javax.crypto.Mac` `HmacSHA256` + `java.security.MessageDigest` `SHA-256`）实现火山 TOS V4 协议（`TOS4-HMAC-SHA256` Header 签名 `PUT` 与 Query 预签名 `GET`）。**不引入第三方对象存储 SDK 依赖**，保持 `api` 与 `worker` 依赖树干净，且可直接用 JDK 内置 `HttpServer` 做端到端「worker PUT 上传 → api 签发 URL → HTTP GET 取回同一字节」闭环单测 |
| 对象键命名规则 | 默认格式：`anytoken/img/<job_id>/<attempt>.png`（若一次生成多张则为 `anytoken/img/<job_id>/<attempt>-<index>.png`）；禁止以 `/` 开头（写入和签名时若传入前导 `/` 需规范化去掉，并拒绝含 `..` 的路径穿越键） |
| TOS URL 路径风格 | 统一采用 Path-Style：`{endpoint}/{bucket}/{object_key}`（`endpoint` 末尾去 `/`；`object_key` 按 `/` 分段做 URI 编码但保留路径分隔符 `/`）。这样既兼容 `https://tos-cn-beijing.volces.com/pilipala-dev/...`，也天然兼容本地测试桩 `http://127.0.0.1:<port>/pilipala-dev/...` |
| TOS V4 签名算法（`TOS4-HMAC-SHA256`） | 火山 TOS V4 标准签名派生：<br>1) 派生密钥（注意火山 TOS V4 直接用 `secretKey` UTF-8 字节作为初始 key，不加前缀）：<br>`kDate = HmacSHA256(secretKey.getBytes(UTF_8), yyyymmdd)`<br>`kRegion = HmacSHA256(kDate, region)`<br>`kService = HmacSHA256(kRegion, "tos")`<br>`kSigning = HmacSHA256(kService, "request")`<br>2) `StringToSign`（4 行，`"\n"` 连接）：<br>`TOS4-HMAC-SHA256\n{iso8601BasicUtc}\n{yyyymmdd}/{region}/tos/request\n{sha256Hex(canonicalRequest)}`<br>其中 `iso8601BasicUtc` 格式为 `yyyyMMdd'T'HHmmss'Z'`（UTC），`yyyymmdd` 为前 8 位 |
| Pre-signed `GET` URL 规则（`api` 侧） | Query 参数（按字典序参与 `CanonicalQueryString`）：<br>- `X-Tos-Algorithm=TOS4-HMAC-SHA256`<br>- `X-Tos-Credential={urlEncode(accessKey + "/" + yyyymmdd + "/" + region + "/tos/request")}`<br>- `X-Tos-Date={iso8601BasicUtc}`<br>- `X-Tos-Expires={presignTtlSeconds}`（默认 `900`，范围 `60..86400`）<br>- `X-Tos-SignedHeaders=host`<br>计算签名时 `CanonicalHeaders` 为 `"host:" + hostHeader + "\n"`，`PayloadHash` 固定为 `"UNSIGNED-PAYLOAD"`，最终在 URL 末尾拼上 `&X-Tos-Signature={signatureHex}` |
| Object `PUT` 规则（`worker` 侧） | 1) **`stub` 模式**（默认 `PILIPALA_TOS_MODE=stub`）：不发外部网络请求，把对象字节写入本地目录 `${pilipala.storage.tos.local-dir}/{bucket}/{object_key}`（自动建父目录，校验 `target.normalize().startsWith(root)` 防路径穿越），便于本地双进程无云密钥联调与核验落盘文件。<br>2) **`http` 模式**（`PILIPALA_TOS_MODE=http`）：向 `{endpoint}/{bucket}/{object_key}` 发送 HTTP `PUT`，请求体为对象字节，携带头 `Content-Type`、`X-Tos-Date`、`X-Tos-Content-Sha256`（请求体 SHA-256 hex）、`Authorization: TOS4-HMAC-SHA256 Credential=..., SignedHeaders=content-type;host;x-tos-content-sha256;x-tos-date, Signature=...`。<br>3) 非 `2xx` 响应、超时或 `IOException` 统一抛出 `TosStorageException` |
| Worker 上游结果转存 TOS 规则 | 在 `AnytokenGatewayAdapter` 中（处于 `ExecuteJob` 的无数据库事务阶段）：<br>1) 当 `gateway.mode=stub` 且 `stub_outcome=succeeded` 时：生成确定性的最小合法 1×1 PNG 字节数组（67 字节标准 PNG header+IHDR+IDAT+IEND），若 `params.stub_tos_outcome == "unavailable"` 则模拟 TOS 故障返回 `PROVIDER_UNAVAILABLE`（`retryable=true`），否则调用 `tosStorage.putObject(objectKey, pngBytes, "image/png")`。<br>2) 当 `gateway.mode=http` 且 Anytoken 返回 `2xx` 时：解析响应体——若 `outputs[].b64_json` 或 `data[].b64_json` 含 Base64 图片字节则解码为 `byte[]`，否则使用最小合法 1×1 PNG 字节作为兜底内容，对每个输出对象键调用 `tosStorage.putObject(key, bytes, "image/png")`。**绝不把 `b64_json` 原文塞进 `provider_task.raw_body`**（`raw_body` 中若长文本超限仍按 4096 截断，或用简短占位避免大 Base64 撑爆日志）。<br>3) 若 `tosStorage.putObject` 抛 `TosStorageException`：捕获并返回 `AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, upstreamRequestId, "TOS_ERROR", rawBodyJson)`，使本次 attempt 按可重试失败记入 `job_step` 与 `provider_task`，**绝不让未成功写入 TOS 的任务进入 `succeeded`** |
| 配置项与环境变量 | `api` 与 `worker` 的 `application.yml` 均在 `pilipala.storage.tos` 下增加全 `${ENV:default}` 配置（见下方详情），仓库零真实云密钥 |

---

## 详细设计规格

### 1. 配置项（`api/src/main/resources/application.yml` 与 `worker/src/main/resources/application.yml`）

在 `api` 和 `worker` 的 `application.yml` 中 `pilipala:` 节点下新增 `storage.tos`：

```yaml
pilipala:
  storage:
    tos:
      # stub = 本地零密钥模式（worker 写 local-dir，api 同样按 V4 规则签出短期 URL）；http = 调真实火山 TOS HTTP 接口
      mode: ${PILIPALA_TOS_MODE:stub}
      endpoint: ${PILIPALA_TOS_ENDPOINT:https://tos-cn-beijing.volces.com}
      region: ${PILIPALA_TOS_REGION:cn-beijing}
      bucket: ${PILIPALA_TOS_BUCKET:pilipala-dev}
      access-key: ${PILIPALA_TOS_ACCESS_KEY:pilipala-local-ak}
      secret-key: ${PILIPALA_TOS_SECRET_KEY:pilipala-local-sk}
      presign-ttl-seconds: ${PILIPALA_TOS_PRESIGN_TTL_SECONDS:900}
      timeout-ms: ${PILIPALA_TOS_TIMEOUT_MS:15000}
      local-dir: ${PILIPALA_TOS_LOCAL_DIR:/tmp/pilipala-tos}
```

校验要求：

- `endpoint`、`region`、`bucket` 不可为空；
- `presign-ttl-seconds` 必须在 60..86400 之间（越界时抛 `IllegalStateException` 或构造期拒绝）；
- 当 `mode=http` 时，`access-key` 与 `secret-key` 不可为空白。

### 2. api 模块：`art.pilipala.api.storage` 包结构与 GetJobResult 改造

在 `api/src/main/java/com/pilipala/api/storage/` 下建立：

```
api/src/main/java/com/pilipala/api/storage/
├── TosProperties.java          # @ConfigurationProperties(prefix = "pilipala.storage.tos")
├── TosUrlSigner.java           # @Component：signGetUrl(String objectKey) / signGetUrl(String objectKey, Instant now)
└── TosV4Signer.java            # 纯函数工具类：TOS4-HMAC-SHA256 派生与预签名计算（无外部依赖，方便单测固定 Clock/Instant 断言）
```

`TosUrlSigner` 行为约束：

```java
String signGetUrl(String objectKey)：
    校验并规范化 objectKey（去前导 /，拒绝空串或含 .. 的键）。
    使用当前 UTC 时间（并提供包可见或重载 signGetUrl(String objectKey, Instant signingTime) 供单测传入固定时间做确定性签名断言）。
    返回完整预签名 URL： {endpoint}/{bucket}/{encodedObjectKey}?X-Tos-Algorithm=TOS4-HMAC-SHA256&X-Tos-Credential=...&X-Tos-Date=...&X-Tos-Expires={ttl}&X-Tos-SignedHeaders=host&X-Tos-Signature={sig}
```

`host` 头取值规则：从 `URI.create(endpoint)` 提取 host，若端口非默认（非 -1、非 80、非 443）则拼为 `host:port`（这保证本地 HttpServer 随机端口如 `127.0.0.1:54321` 时签名与服务端验签完全一致）。

`GetJobResult` 改造（`api/src/main/java/com/pilipala/api/job/application/GetJobResult.java`）：

- 注入 `TosUrlSigner`。
- 当 `job.status() == JobStatus.SUCCEEDED` 时，遍历 `job.result_object_keys`：

```java
String key = item.asText();
if (key != null && !key.isBlank()) {
    outputs.add(new JobOutput("image", key, tosUrlSigner.signGetUrl(key)));
}
```

- 当 `job.status() != JobStatus.SUCCEEDED` 时，`outputs` 仍返回 `List.of()`（不为未完成或失败的任务生成签名 URL）。

### 3. worker 模块：`art.pilipala.worker.storage` 包结构与 AnytokenGatewayAdapter 改造

在 `worker/src/main/java/com/pilipala/worker/storage/` 下建立：

```
worker/src/main/java/com/pilipala/worker/storage/
├── TosProperties.java              # @ConfigurationProperties(prefix = "pilipala.storage.tos")
├── TosObjectStorage.java           # 唯一火山 TOS 写接口：void putObject(String objectKey, byte[] content, String contentType)
├── TosObjectStorageAdapter.java    # @Component：实现 stub（本地落盘）与 http（V4 Header 签名 PUT）双模式
├── TosStorageException.java        # 运行时异常（只含脱敏错误摘要与状态码，不含 AK/SK）
├── TosV4Signer.java                # TOS4-HMAC-SHA256 PUT Header 签名工具
└── MinimalPng.java                 # 67 字节标准 1×1 PNG 常量工具（供 stub 模式及无 b64 响应兜底使用）
```

`TosObjectStorageAdapter` 行为约束：

- 校验 `objectKey`（去前导 `/`，拒绝空串或含 `..` 的路径穿越）、校验 `content != null && content.length > 0`。
- `mode = "stub"`：
  - 目标路径为 `Path.of(properties.localDir()).resolve(properties.bucket()).resolve(normalizedKey).normalize()`。
  - 必须校验 `target.startsWith(Path.of(properties.localDir()).normalize())`，防止路径穿越。
  - 创建父目录并写入 content 字节（`Files.write`）。
- `mode = "http"`：
  - 向 `{endpoint}/{bucket}/{encodedObjectKey}` 发起 HTTP `PUT`。
  - 计算 `contentSha256 = sha256Hex(content)`。
  - 设置 Header：
    - `Content-Type: contentType`（默认 `image/png`）
    - `X-Tos-Date: iso8601BasicUtc`
    - `X-Tos-Content-Sha256: contentSha256`
    - `Authorization: TOS4-HMAC-SHA256 Credential={accessKey}/{yyyymmdd}/{region}/tos/request, SignedHeaders=content-type;host;x-tos-content-sha256;x-tos-date, Signature={sig}`
  - 若响应状态码 `< 200 || >= 300`，或发生 `HttpTimeoutException` / `IOException` / `InterruptedException`：抛出 `TosStorageException`（附带 HTTP 状态码或异常类型摘要，绝不包含 accessKey / secretKey / Authorization 头）。

`AnytokenGatewayAdapter` 改造（`worker/src/main/java/com/pilipala/worker/gateway/AnytokenGatewayAdapter.java`）：

- 构造函数新增注入 `TosObjectStorage tosStorage`。
- `stub` 模式：
  - 当 `stub_outcome` 为成功分支时：
    - 若 `params.path("stub_tos_outcome").asText("")` 为 `"unavailable"`，直接返回 `AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, upstreamRequestId, "TOS_503", stubRawBody(503, "tos storage unavailable"))`（方便单测与本地 curl 测试 TOS 故障触发重试 → dead → 退回钱包冻结额）。
    - 否则对 `objectKey = "anytoken/img/" + request.jobId() + "/" + request.attempt() + ".png"` 调用 `tosStorage.putObject(objectKey, MinimalPng.BYTES, "image/png")`，成功后再返回 `AnytokenAttemptResult.success(List.of(objectKey), ...)`。
- `http` 模式：
  - 当 Anytoken 返回 2xx 且 JSON 解析成功时：
    - 提取输出项：支持 `outputs[]` 或 `data[]` 中的 `b64_json`（解码 Base64 得到 `byte[]`；若 Base64 非法则返回 `PROVIDER_UNAVAILABLE` 可重试失败），若上游只返回了 `object_keys` / `outputs[].object_key` 或无 `b64_json`，则使用 `MinimalPng.BYTES`。
    - 对每个生成的对象键调用 `tosStorage.putObject(key, bytes, "image/png")`。
    - 若 `tosStorage.putObject` 抛出 `TosStorageException`，捕获并返回 `AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, upstreamRequestId, "TOS_" + e.status(), tosFailureRawBody(status, e.getMessage()))`。
- 脱敏硬性要求：如果上游响应 JSON 里带有很长的 `b64_json`，写 `provider_task.raw_body` 前仍遵循最多 4096 字符截断规则，且 `raw_body` 与日志绝不出现 `PILIPALA_GATEWAY_API_KEY`、`PILIPALA_TOS_ACCESS_KEY`、`PILIPALA_TOS_SECRET_KEY`。

### 4. 文档同步要求

- 新增本 brief 文件：`docs/engineering/feature-briefs/2026-10-24-wp8-tos-storage-signed-url.md`（内容与本 brief 一致）。
- 更新 `docs/engineering/feature-briefs/README.md`：
  - 将 WP6 状态从「已实现，待验收」更新为「已实现」；
  - 新增 WP8 行：`| [2026-10-24-wp8-tos-storage-signed-url.md](./2026-10-24-wp8-tos-storage-signed-url.md) | WP8 / M2 | 火山 TOS 对象存储与短期签名读 URL（worker PUT + api V4 预签名 GET） | 已实现，待验收 |`。
- 更新 `docs/engineering/api/gateway-contract.md`：
  - 在 `### Result` 小节把 `url` 说明更新为：由 api 用火山 TOS V4（`TOS4-HMAC-SHA256`）签发短期预签名 GET 地址（默认 TTL 900s，WP8 已落地）；worker 只写 `object_key`。
- 更新 `docs/engineering/runbooks/local-dev.md`：
  - 增加 WP8 环境变量表（`PILIPALA_TOS_MODE`、`PILIPALA_TOS_ENDPOINT`、`PILIPALA_TOS_REGION`、`PILIPALA_TOS_BUCKET`、`PILIPALA_TOS_ACCESS_KEY`、`PILIPALA_TOS_SECRET_KEY`、`PILIPALA_TOS_PRESIGN_TTL_SECONDS`、`PILIPALA_TOS_TIMEOUT_MS`、`PILIPALA_TOS_LOCAL_DIR`）；
  - 增加双进程联调验证步骤：
    - 提交成功任务后，调用 `GET /api/v1/jobs/<job_id>/result`，验证 `outputs[0].object_key` 为 `anytoken/img/<job_id>/1.png`，且 `outputs[0].url` 非空、包含 `X-Tos-Algorithm=TOS4-HMAC-SHA256`、`X-Tos-Expires=900`、`X-Tos-Signature=`；
    - 验证本地 stub 模式下 `/tmp/pilipala-tos/pilipala-dev/anytoken/img/<job_id>/1.png` 文件真实落盘且文件头为合法 PNG（`89 50 4e 47`）；
    - 提交带 `"stub_tos_outcome": "unavailable"` 的任务，验证 TOS 写入失败时任务会重试 3 次后进 dead、错误码为 `PROVIDER_UNAVAILABLE`，且钱包冻结额自动 release 退回 available；
  - 更新文末「已验证 vs 还未做」对照表（把「TOS 对象存储与短期签名 URL」移入「已实现」；注明默认 `PILIPALA_TOS_MODE=stub` 写本地 `/tmp/pilipala-tos`，配齐真实火山 TOS 桶与 AK/SK 并设 `PILIPALA_TOS_MODE=http` 即可直连线上 TOS；标注 WP9 `i18n_message` 表、WP7 前端对接、WP10/WP11 尚未做）。

## 验收标准（Checklist）

- [ ] `docs/engineering/feature-briefs/2026-10-24-wp8-tos-storage-signed-url.md` 已入仓，索引 `feature-briefs/README.md`、`api/gateway-contract.md`、`runbooks/local-dev.md` 已同步更新
- [ ] 无新增 Flyway 迁移文件，且 `V1` / `V2` / `V3` / `V4` 迁移文件 0 行改动
- [ ] `worker` 新增 `art.pilipala.worker.storage` 包（`TosProperties`、`TosObjectStorage`、`TosObjectStorageAdapter`、`TosStorageException`、`TosV4Signer`、`MinimalPng`），无多云抽象接口
- [ ] `api` 新增 `art.pilipala.api.storage` 包（`TosProperties`、`TosUrlSigner`、`TosV4Signer`），`GetJobResult` 在 `job.status == SUCCEEDED` 时返回非空预签名 url（含 `X-Tos-Algorithm=TOS4-HMAC-SHA256`、`X-Tos-Credential`、`X-Tos-Date`、`X-Tos-Expires`、`X-Tos-SignedHeaders=host`、`X-Tos-Signature`）
- [ ] 当任务未成功（queued / running / cancelled / dead）时，`GET /api/v1/jobs/{jobId}/result` 的 `outputs` 仍为空列表 `[]`
- [ ] `worker` 在 stub 模式下成功执行任务后，将合法 PNG 字节写入 `${PILIPALA_TOS_LOCAL_DIR}/{bucket}/anytoken/img/<job_id>/<attempt>.png`，且防路径穿越校验生效
- [ ] `worker` 在 http 模式下使用 `TOS4-HMAC-SHA256` Header 签名向 `{endpoint}/{bucket}/{object_key}` 发起 PUT 请求（含 `X-Tos-Date`、`X-Tos-Content-Sha256`、`Authorization`）
- [ ] 端到端对象取回验证：在单测中用 JDK HttpServer 搭建本地 TOS 服务端桩（同时校验 PUT 的 `TOS4-HMAC-SHA256` Header 签名与 GET 的 Query 预签名），验证 worker 上传生成的 PNG 后，拿 api `TosUrlSigner` 签出的 url 发起 HTTP GET 能返回 200 且取回完全一致的 PNG 字节
- [ ] TOS 故障不吞冻、不假成功：当 TOS PUT 失败（如 HTTP 500 / 超时 / `stub_tos_outcome=unavailable`）时，worker 将本次 attempt 记为 `PROVIDER_UNAVAILABLE`（`retryable=true`），不写 `job.result_object_keys`，重试耗尽后进 dead 并由 api 自动 release 退回钱包冻结额
- [ ] 脱敏与边界：`provider_task.raw_body`、日志、MQ 消息体与 C 端响应中 **绝不** 出现 `PILIPALA_TOS_SECRET_KEY`、`PILIPALA_TOS_ACCESS_KEY`、`PILIPALA_GATEWAY_API_KEY`；MQ 消息体与 Postgres 表不存图片字节
- [ ] worker 编译依赖与包内仍然零 sa-token、零 wallet/billing、零 canvas、零 newapi
- [ ] `./mvnw test` 全部单测通过（覆盖 `TosV4Signer` 确定性向量、`TosUrlSigner`、`TosObjectStorageAdapter` stub/http 模式、路径穿越拒绝、端到端 PUT+预签名 GET 取回字节、TOS 失败重试与退费闭环）
- [ ] 分支 `feat/wp8-tos-storage-signed-url`；PR base = `main`；无真实密钥入库；不要自行 merge

## 非目标

- 不做阿里云 OSS / AWS S3 / 腾讯云 COS 多云抽象层
- 不做前端直传 TOS（预签名 PUT 留给后续需要的资产上传 WP，本 WP 只做生成结果落 TOS + C 端预签名 GET 读地址）
- 不建数据库新表（不把 TOS 对象元数据再抄一份建成所谓的 `tos_object` 表）
- 不修改前端 `web/` 目录

## 给 Minimax 的额外约束

- **先读再写**：`DEVELOPMENT.md`（WP8）、`TECH_STACK.md`、ADR 0001 / 0003 / 0004 / 0005、`data-model/README.md`、`data-model/jobs.md`、`api/gateway-contract.md`、现有 `GetJobResult.java`、`ExecuteJob.java`、`AnytokenGatewayAdapter.java`、`runbooks/local-dev.md`。
- **注意现有单测更新**：
  - api 的 `JobLifecycleTest` / `JobControllerSliceTest` 之前断言 `result.outputs().get(0).url()` 为 null；接入 `TosUrlSigner` 后请更新断言为非空且包含 `X-Tos-Algorithm=TOS4-HMAC-SHA256` 与 `X-Tos-Signature=`。
  - worker 的 `AnytokenGatewayAdapterTest` / `ExecuteJobTest` 构造 `AnytokenGatewayAdapter` 时传入 `TosObjectStorage`（单测可用 `@TempDir` 配置 stub 模式的 `TosObjectStorageAdapter` 或内存桩）。
- **全部测试必须绿**：`./mvnw test` 同时跑通 api 与 worker 全部单测。
- **做完用简短中文汇报**：改了哪些模块、本地双进程 + curl 实测结果（成功任务返回签名 url + `/tmp/pilipala-tos` PNG 文件落盘核验 + `stub_tos_outcome=unavailable` 触发 3 次重试进 dead 并自动退回钱包冻结额）、单测覆盖情况、故意没做哪些（多云抽象 / 前端改动 / 新建 DB 表）。

## 留给实现者的选择（最多拍这 1 项，PR 写明选了哪样）

`TosV4Signer` 在 api 与 worker 间的代码组织：

- **方式 A（推荐）**：由于 api 与 worker 是两个独立模块且没有共享 common 子模块，分别在 `art.pilipala.api.storage.TosV4Signer` 与 `art.pilipala.worker.storage.TosV4Signer` 中放置小巧的 V4 HMAC-SHA256 签名工具类（各 ~100 行，无额外模块改造，保持 Maven 结构与 pom.xml 最简）；
- **方式 B**：只在各自包内实现所需最小方法（api 只实现 Query 预签名 GET，worker 只实现 Header 签名 PUT，派生密钥 4 步公式保持一致）。

无论选哪种，**不要新增第三个 Maven 子模块**，且两边的密钥派生逻辑必须可通过同一组测试向量验证。

不要再自行扩大其它开放问题。

## 分支 / Commit / PR 指令

```bash
git fetch origin
git checkout main
git pull origin main
git checkout -b feat/wp8-tos-storage-signed-url
git add docs/engineering api worker
git commit -m "WP8: 火山 TOS 结果存储与短期预签名读 URL"
git push -u origin feat/wp8-tos-storage-signed-url
```

开 PR → `main`：

- 标题：`WP8: 火山 TOS 对象存储与短期签名读 URL`
- 正文必须含：brief 路径 `docs/engineering/feature-briefs/2026-10-24-wp8-tos-storage-signed-url.md`、做了什么、验收自勾、故意没做（无多云抽象 / 无新增 DB 表 / 无前端改动）、本地双进程 curl + 落盘文件 + 失败退费实测记录、实现者选择结论。
- **不要自行 merge。**
