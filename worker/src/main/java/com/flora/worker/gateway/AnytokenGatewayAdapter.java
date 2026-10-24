package com.flora.worker.gateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flora.worker.job.domain.WorkerErrorCodes;
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
 * <p>脱敏：只把上游响应体摘要写进 raw_body（截断 4096 字符），
 * **绝不**把 apiKey 或 Authorization 头写进 raw_body 或日志。
 */
@Component
public class AnytokenGatewayAdapter implements AnytokenGateway {

    private static final Logger log = LoggerFactory.getLogger(AnytokenGatewayAdapter.class);

    /** 上游响应体写进 raw_body 前的长度上限。 */
    private static final int MAX_RAW_BODY_CHARS = 4096;
    private static final int MAX_STUB_DELAY_MS = 10_000;

    private final AnytokenProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public AnytokenGatewayAdapter(AnytokenProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
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
            default -> AnytokenAttemptResult.success(
                    List.of("anytoken/img/" + request.jobId() + "/" + request.attempt() + ".png"),
                    upstreamRequestId, "200", stubSuccessRawBody(request.model()));
        };
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
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.baseUrl() + properties.generatePath()))
                .timeout(Duration.ofMillis(Math.max(1, properties.timeoutMs())))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson(request)));
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.apiKey());
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
            return AnytokenAttemptResult.success(
                    outputKeys(request, body),
                    upstreamRequestId(response, body),
                    String.valueOf(status),
                    rawBody);
        }
        if (status >= 400 && status < 500 && status != 429) {
            // 客户端业务拒绝：不可重试。
            return AnytokenAttemptResult.failure(WorkerErrorCodes.JOB_FAILED, false,
                    upstreamRequestId(response, null), String.valueOf(status), rawBody);
        }
        return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true,
                upstreamRequestId(response, null), String.valueOf(status), rawBody);
    }

    private List<String> outputKeys(AnytokenRequest request, JsonNode body) {
        List<String> keys = new java.util.ArrayList<>();
        JsonNode objectKeys = body.path("object_keys");
        if (objectKeys.isArray()) {
            objectKeys.forEach(node -> keys.add(node.asText()));
        }
        JsonNode outputs = body.path("outputs");
        if (outputs.isArray()) {
            outputs.forEach(node -> {
                String key = node.path("object_key").asText(null);
                if (key != null && !key.isBlank()) {
                    keys.add(key);
                }
            });
        }
        if (keys.isEmpty()) {
            // 上游没给对象键：兜底合成一个（WP8 之前没有 TOS，也就没有签名 URL）。
            keys.add("anytoken/img/" + request.jobId() + "/" + request.attempt() + ".png");
        }
        return keys;
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
        node.set("params", readParams(request.paramsJson()));
        return node.toString();
    }

    /** 上游响应体摘要：截断到 4096 字符，超出时标 truncated。 */
    private String rawBodyJson(int httpStatus, String bodyText) {
        ObjectNode node = mapper.createObjectNode();
        node.put("http_status", httpStatus);
        String text = bodyText == null ? "" : bodyText;
        boolean truncated = text.length() > MAX_RAW_BODY_CHARS;
        node.put("body_excerpt", truncated ? text.substring(0, MAX_RAW_BODY_CHARS) : text);
        if (truncated) {
            node.put("truncated", true);
        }
        return node.toString();
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
}
