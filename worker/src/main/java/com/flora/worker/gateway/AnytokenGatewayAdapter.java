package com.flora.worker.gateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flora.worker.job.domain.WorkerErrorCodes;
import com.flora.worker.storage.MinimalPng;
import com.flora.worker.storage.TosObjectStorage;
import com.flora.worker.storage.TosStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 唯一 Anytoken 适配器的实现：stub 与 http 两种模式，归一成
 * {@link AnytokenAttemptResult}。
 *
 * <p>stub（默认）：不发外部请求，按 params 里的 {@code stub_outcome} 确定性返回
 * 成功 / 可重试失败（PROVIDER_UNAVAILABLE）/ 不可重试失败（JOB_FAILED），
 * 让本地 curl 与单测零成本跑通全链路。
 *
 * <p>http：真调 Anytoken。错误映射：4xx（429 除外）→ JOB_FAILED（不可重试）；
 * 429 / 5xx / 网络异常 / 超时 / 非法 JSON → PROVIDER_UNAVAILABLE（可重试）。
 *
 * <p>WP8 起：生成结果的字节在这里写进火山 TOS，job 里只留对象键
 * （{@code job.result_object_keys}）。TOS 写失败一律归一成
 * {@code PROVIDER_UNAVAILABLE}（可重试），**绝不**让没写进 TOS 的任务算成功。
 *
 * <p>脱敏：只把上游响应体摘要写进 raw_body（截断 4096 字符），
 * Base64 图片字节会被替换成占位符，**绝不**把 apiKey、TOS 密钥或
 * Authorization 头写进 raw_body 或日志。
 */
@Component
public class AnytokenGatewayAdapter implements AnytokenGateway {

    private static final Logger log = LoggerFactory.getLogger(AnytokenGatewayAdapter.class);

    /** 上游响应体写进 raw_body 前的长度上限。 */
    private static final int MAX_RAW_BODY_CHARS = 4096;
    private static final int MAX_STUB_DELAY_MS = 10_000;

    private final AnytokenProperties properties;
    private final ObjectMapper mapper;
    private final TosObjectStorage tosStorage;
    private final HttpClient httpClient;

    public AnytokenGatewayAdapter(AnytokenProperties properties, ObjectMapper mapper, TosObjectStorage tosStorage) {
        this.properties = properties;
        this.mapper = mapper;
        this.tosStorage = tosStorage;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, properties.timeoutMs())))
                .build();
    }

    @Override
    public AnytokenAttemptResult execute(AnytokenRequest request) {
        String mode = properties.mode() == null ? "stub" : properties.mode();
        return switch (mode) {
            case "stub" -> stub(request);
            case "http" -> http(request);
            default -> throw new IllegalStateException("未知的 flora.gateway.mode：" + mode);
        };
    }

    // ---------------------------------------------------------------- stub

    private AnytokenAttemptResult stub(AnytokenRequest request) {
        JsonNode params = readParams(request.paramsJson());
        String outcome = params.path("stub_outcome").asText("succeeded");
        sleep(params.path("stub_delay_ms").asInt(0));

        String upstreamRequestId = "stub-" + request.jobId() + "-" + request.attempt();
        return switch (outcome) {
            case "provider_unavailable" -> AnytokenAttemptResult.failure(
                    WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, upstreamRequestId, "503",
                    stubRawBody(503, "upstream channel overloaded"));
            case "job_failed" -> AnytokenAttemptResult.failure(
                    WorkerErrorCodes.JOB_FAILED, false, upstreamRequestId, "400",
                    stubRawBody(400, "invalid generation parameters"));
            default -> storeStubResult(request, params, upstreamRequestId);
        };
    }

    /** stub 成功分支：把最小 PNG 写进 TOS，再返回对象键。 */
    private AnytokenAttemptResult storeStubResult(AnytokenRequest request, JsonNode params, String upstreamRequestId) {
        if ("unavailable".equals(params.path("stub_tos_outcome").asText(""))) {
            return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, upstreamRequestId,
                    "TOS_503", stubRawBody(503, "tos storage unavailable"));
        }
        String objectKey = defaultObjectKey(request);
        try {
            tosStorage.putObject(objectKey, MinimalPng.BYTES, "image/png");
        } catch (TosStorageException e) {
            return tosFailure(upstreamRequestId, e);
        }
        return AnytokenAttemptResult.success(List.of(objectKey), upstreamRequestId, "200",
                stubSuccessRawBody(request.model()));
    }

    private String stubSuccessRawBody(String model) {
        ObjectNode node = mapper.createObjectNode();
        node.put("provider", "anytoken");
        node.put("mode", "stub");
        node.put("status", "succeeded");
        node.put("model", model);
        return node.toString();
    }

    private String stubRawBody(int httpStatus, String error) {
        ObjectNode node = mapper.createObjectNode();
        node.put("provider", "anytoken");
        node.put("mode", "stub");
        node.put("http_status", httpStatus);
        node.put("error", error);
        return node.toString();
    }

    // ---------------------------------------------------------------- http

    private AnytokenAttemptResult http(AnytokenRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolveGenerateUri())
                .timeout(Duration.ofMillis(Math.max(1, properties.timeoutMs())))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson(request)));
        String effectiveKey = properties.apiKeyForCapability(request.capability());
        if (effectiveKey != null && !effectiveKey.isBlank()) {
            builder.header("Authorization", "Bearer " + effectiveKey);
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return fromResponse(request, response);
        } catch (HttpTimeoutException e) {
            return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, null, "TIMEOUT", rawBodyJson(0, ""));
        } catch (IOException e) {
            return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, null, "IO_ERROR", rawBodyJson(0, ""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, null, "INTERRUPTED", rawBodyJson(0, ""));
        }
    }

    /**
     * 真实上游地址：{@code baseUrl} 末尾的 {@code /v1} 与 {@code generatePath} 开头的
     * {@code /v1/} 只保留一个，避免拼出 {@code /v1/v1/...}。
     */
    URI resolveGenerateUri() {
        String base = (properties.baseUrl() == null ? "" : properties.baseUrl().trim()).replaceAll("/+$", "");
        String path = (properties.generatePath() == null || properties.generatePath().isBlank())
                ? "/v1/images/generations"
                : properties.generatePath().trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (base.toLowerCase(Locale.ROOT).endsWith("/v1")
                && path.toLowerCase(Locale.ROOT).startsWith("/v1/")) {
            path = path.substring(3);
        }
        return URI.create(base + path);
    }

    private AnytokenAttemptResult fromResponse(AnytokenRequest request, HttpResponse<String> response) {
        int status = response.statusCode();
        String rawBody = rawBodyJson(status, response.body());

        if (status >= 200 && status < 300) {
            JsonNode body = parseBody(response.body());
            if (body == null) {
                // 2xx 但响应不是合法 JSON：按「未知响应格式」处理，可重试。
                return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, null,
                        String.valueOf(status), rawBody);
            }
            return storeHttpResult(request, body, upstreamRequestId(response, body), status, rawBody);
        }
        if (status >= 400 && status < 500 && status != 429) {
            // 客户端业务拒绝：不可重试。
            return AnytokenAttemptResult.failure(WorkerErrorCodes.JOB_FAILED, false,
                    upstreamRequestId(response, null), String.valueOf(status), rawBody);
        }
        return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true,
                upstreamRequestId(response, null), String.valueOf(status), rawBody);
    }

    /** http 成功分支：把每个输出写进 TOS，再返回对象键列表。 */
    private AnytokenAttemptResult storeHttpResult(AnytokenRequest request, JsonNode body, String upstreamRequestId,
            int status, String rawBody) {
        List<StoredOutput> outputs;
        try {
            outputs = extractOutputs(request, body);
        } catch (IllegalArgumentException e) {
            // Base64 图片字节非法：按可重试失败处理。
            return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, upstreamRequestId,
                    String.valueOf(status), rawBody);
        }
        List<String> keys = new ArrayList<>();
        for (StoredOutput output : outputs) {
            try {
                tosStorage.putObject(output.key(), output.bytes(), "image/png");
            } catch (TosStorageException e) {
                return tosFailure(upstreamRequestId, e);
            }
            keys.add(output.key());
            log.debug("生成结果已写入 TOS key={} bytes={}", output.key(), output.bytes().length);
        }
        return AnytokenAttemptResult.success(keys, upstreamRequestId, String.valueOf(status), rawBody);
    }

    /** 上游输出 → 待写入 TOS 的对象键与字节。 */
    private List<StoredOutput> extractOutputs(AnytokenRequest request, JsonNode body) {
        List<StoredOutput> outputs = new ArrayList<>();
        JsonNode objectKeys = body.path("object_keys");
        if (objectKeys.isArray()) {
            objectKeys.forEach(node -> outputs.add(new StoredOutput(node.asText(), MinimalPng.BYTES)));
        }
        collectOutputs(body.path("outputs"), request, outputs);
        collectOutputs(body.path("data"), request, outputs);
        if (outputs.isEmpty()) {
            // 上游没给输出：兜底合成一个键 + 最小 PNG 字节。
            outputs.add(new StoredOutput(defaultObjectKey(request), MinimalPng.BYTES));
        }
        return outputs;
    }

    private void collectOutputs(JsonNode array, AnytokenRequest request, List<StoredOutput> outputs) {
        if (!array.isArray()) {
            return;
        }
        for (JsonNode node : array) {
            if (node.isTextual()) {
                outputs.add(new StoredOutput(node.asText(), MinimalPng.BYTES));
                continue;
            }
            String base64 = node.path("b64_json").asText(null);
            String objectKey = node.path("object_key").asText(null);
            if (base64 != null && !base64.isBlank()) {
                // 非法 Base64 会抛 IllegalArgumentException，由调用方归一成可重试失败。
                outputs.add(new StoredOutput(
                        objectKey == null || objectKey.isBlank() ? defaultObjectKey(request) : objectKey,
                        Base64.getDecoder().decode(base64)));
            } else if (objectKey != null && !objectKey.isBlank()) {
                outputs.add(new StoredOutput(objectKey, MinimalPng.BYTES));
            } else {
                // 真实上游可能只给一个临时图片 URL：下载字节后再写进 TOS。
                String url = node.path("url").asText(null);
                if (url != null && !url.isBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    outputs.add(new StoredOutput(defaultObjectKey(request), downloadImageBytes(url)));
                }
            }
        }
    }

    /**
     * 下载上游给的临时图片字节。
     *
     * <p>失败一律抛 {@link IllegalArgumentException}（不含 URL，避免签名泄露），
     * 由 {@link #storeHttpResult} 归一成可重试的 PROVIDER_UNAVAILABLE。
     */
    private byte[] downloadImageBytes(String url) {
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofMillis(Math.max(1, properties.timeoutMs())))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300 || bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("Failed to download upstream image url");
            }
            return bytes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Failed to download upstream image url");
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to download upstream image url");
        }
    }

    private String defaultObjectKey(AnytokenRequest request) {
        return "anytoken/img/" + request.jobId() + "/" + request.attempt() + ".png";
    }

    /** TOS 写失败：统一归一成可重试的 PROVIDER_UNAVAILABLE。 */
    private AnytokenAttemptResult tosFailure(String upstreamRequestId, TosStorageException e) {
        String status = e.status() > 0 ? "TOS_" + e.status() : "TOS_ERROR";
        ObjectNode node = mapper.createObjectNode();
        node.put("provider", "anytoken");
        node.put("stage", "tos");
        node.put("http_status", e.status());
        node.put("error", e.getMessage());
        return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, upstreamRequestId,
                status, node.toString());
    }

    private String upstreamRequestId(HttpResponse<String> response, JsonNode body) {
        if (body != null) {
            String id = body.path("id").asText(null);
            if (id != null && !id.isBlank()) {
                return id;
            }
            String requestId = body.path("request_id").asText(null);
            if (requestId != null && !requestId.isBlank()) {
                return requestId;
            }
        }
        return response.headers().firstValue("x-request-id").orElse(null);
    }

    private String requestBodyJson(AnytokenRequest request) {
        ObjectNode node = mapper.createObjectNode();
        node.put("job_id", request.jobId().toString());
        node.put("attempt", request.attempt());
        node.put("capability", request.capability());
        node.put("model", request.model());
        JsonNode params = readParams(request.paramsJson());
        node.set("params", params);

        // 真实 Anytoken 认的是标准 OpenAI /v1/images/generations：把生图字段提升到顶层。
        String prompt = params.path("prompt").asText("");
        if (!prompt.isBlank()) {
            node.put("prompt", prompt);
        }
        node.put("n", Math.max(1, params.path("n").asInt(1)));
        String size = normalizeOpenAiImageSize(params.path("size").asText(""));
        if (!size.isBlank()) {
            node.put("size", size);
        }
        String quality = params.path("quality").asText("");
        if ("low".equals(quality) || "medium".equals(quality) || "high".equals(quality)) {
            node.put("quality", quality);
        }
        // gpt-image 系列默认就返回 base64，显式带 response_format 反而可能被上游拒绝。
        if (request.model() == null || !request.model().toLowerCase(Locale.ROOT).contains("gpt-image")) {
            node.put("response_format", "b64_json");
        }
        return node.toString();
    }

    /**
     * 把比例尺寸翻成 OpenAI 认的像素尺寸；已经是 {@code 1024x1024} 的原样保留。
     *
     * @return 空串表示「不要写顶层 size」
     */
    static String normalizeOpenAiImageSize(String rawSize) {
        if (rawSize == null) {
            return "";
        }
        String size = rawSize.trim().toLowerCase(Locale.ROOT);
        if (size.isEmpty() || "auto".equals(size)) {
            return "";
        }
        return switch (size) {
            case "1:1" -> "1024x1024";
            case "16:9", "3:2" -> "1536x1024";
            case "9:16", "2:3" -> "1024x1536";
            default -> size.matches("\\d+x\\d+") ? size : "";
        };
    }

    /** 上游响应体摘要：脱敏（去掉 Base64 图片字节）后截断到 4096 字符。 */
    private String rawBodyJson(int httpStatus, String bodyText) {
        ObjectNode node = mapper.createObjectNode();
        node.put("http_status", httpStatus);
        String text = sanitizeBody(bodyText);
        boolean truncated = text.length() > MAX_RAW_BODY_CHARS;
        node.put("body_excerpt", truncated ? text.substring(0, MAX_RAW_BODY_CHARS) : text);
        if (truncated) {
            node.put("truncated", true);
        }
        return node.toString();
    }

    /** JSON 里的 b64_json 换成占位符：图片字节不进 raw_body、不进日志。 */
    private String sanitizeBody(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return "";
        }
        JsonNode node = parseBody(bodyText);
        if (node == null) {
            return bodyText;
        }
        return redactBase64(node).toString();
    }

    private JsonNode redactBase64(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = mapper.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                if ("b64_json".equals(entry.getKey())) {
                    copy.put(entry.getKey(), "<omitted base64 image bytes>");
                } else {
                    copy.set(entry.getKey(), redactBase64(entry.getValue()));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = mapper.createArrayNode();
            node.forEach(item -> copy.add(redactBase64(item)));
            return copy;
        }
        return node;
    }

    private JsonNode parseBody(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(bodyText);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private JsonNode readParams(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            JsonNode node = mapper.readTree(paramsJson);
            return node.isObject() ? node : mapper.createObjectNode();
        } catch (JsonProcessingException e) {
            return mapper.createObjectNode();
        }
    }

    private void sleep(int millis) {
        int delay = Math.max(0, Math.min(millis, MAX_STUB_DELAY_MS));
        if (delay == 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 待写入 TOS 的一个输出。 */
    private record StoredOutput(String key, byte[] bytes) {
    }
}
