package art.pilipala.worker.gateway.newapi;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import art.pilipala.worker.job.domain.WorkerErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * NewAPI 视频网关的 HTTP 客户端。
 *
 * <p>只负责提交 / 查询与把上游形状归一成 {@link NewApiSubmitResult} / {@link NewApiTaskStatus}；
 * 不写 TOS、不改 job 状态、不做 fallback。密钥只从 {@link NewApiProperties} 读，不写日志。
 *
 * <p>WP21 实测的关键协议：
 * <ul>
 *   <li>提交：{@code POST /v1/video/generations}，body 至少 model/prompt/duration/size；</li>
 *   <li>查询：{@code GET /v1/video/generations/{id}}，成功态 {@code SUCCEEDED}，成片 URL 在 {@code object}；</li>
 *   <li>失败态是 {@code FAILED: <message>}，不是精确的 FAILED；</li>
 *   <li>不存在的 task 返回 400 {@code task_not_exist}；</li>
 *   <li>{@code /content} 端点 404，不要依赖。</li>
 * </ul>
 */
@Service
public class HttpNewApiVideoClient implements NewApiVideoClient {

    private static final Logger log = LoggerFactory.getLogger(HttpNewApiVideoClient.class);
    private static final int MAX_RAW_BODY_CHARS = 4096;

    private final NewApiProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public HttpNewApiVideoClient(NewApiProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1, properties.timeoutMs())))
                .build();
    }

    @Override
    public NewApiSubmitResult submit(NewApiVideoRequest request) {
        if (!properties.configured()) {
            return NewApiSubmitResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, false, "CONFIG",
                    "newapi not configured");
        }
        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder(URI.create(properties.generateUrl()))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(submitBody(request)))
                    .build();
        } catch (IllegalArgumentException e) {
            return NewApiSubmitResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, false, "CONFIG",
                    "invalid newapi base url");
        }
        try {
            return parseSubmit(httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString()));
        } catch (HttpTimeoutException e) {
            return NewApiSubmitResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, "TIMEOUT", "");
        } catch (IOException e) {
            return NewApiSubmitResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, "IO_ERROR", "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NewApiSubmitResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, "INTERRUPTED", "");
        }
    }

    @Override
    public NewApiTaskStatus query(String taskId) {
        if (!properties.configured()) {
            return NewApiTaskStatus.failed(taskId, null, WorkerErrorCodes.PROVIDER_UNAVAILABLE,
                    "newapi not configured", "CONFIG", "");
        }
        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder(URI.create(properties.statusUrl(taskId)))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            return NewApiTaskStatus.failed(taskId, null, WorkerErrorCodes.PROVIDER_UNAVAILABLE,
                    "invalid newapi base url", "CONFIG", "");
        }
        try {
            return parseQuery(taskId, httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString()));
        } catch (HttpTimeoutException e) {
            return NewApiTaskStatus.transientFailure(taskId, null, "timeout", "TIMEOUT", "");
        } catch (IOException e) {
            return NewApiTaskStatus.transientFailure(taskId, null, "io error", "IO_ERROR", "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NewApiTaskStatus.transientFailure(taskId, null, "interrupted", "INTERRUPTED", "");
        }
    }

    private String submitBody(NewApiVideoRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", request.model());
        body.put("prompt", request.prompt());
        if (request.durationSeconds() > 0) {
            body.put("duration", request.durationSeconds());
        }
        if (request.size() != null && !request.size().isBlank()) {
            body.put("size", request.size());
        }
        return body.toString();
    }

    private NewApiSubmitResult parseSubmit(HttpResponse<String> response) {
        int httpStatus = response.statusCode();
        String rawBody = truncate(response.body());
        String rawStatus = String.valueOf(httpStatus);
        if (httpStatus >= 200 && httpStatus < 300) {
            JsonNode node = parse(rawBody);
            String taskId = text(node, "id", "task_id");
            if (taskId == null && node != null) {
                taskId = text(node.path("data"), "id");
            }
            if (taskId == null) {
                return NewApiSubmitResult.failure(WorkerErrorCodes.GATEWAY_INVALID_RESPONSE, false, rawStatus, rawBody);
            }
            return NewApiSubmitResult.accepted(taskId, text(node, "status"), rawStatus, rawBody);
        }
        if (httpStatus == 429 || httpStatus >= 500) {
            log.warn("newapi submit transient failure httpStatus={}", httpStatus);
            return NewApiSubmitResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, rawStatus, rawBody);
        }
        if (httpStatus == 401 || httpStatus == 403) {
            log.warn("newapi submit auth failure httpStatus={}", httpStatus);
            return NewApiSubmitResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, false, rawStatus, rawBody);
        }
        log.warn("newapi submit rejected httpStatus={}", httpStatus);
        return NewApiSubmitResult.failure(WorkerErrorCodes.JOB_FAILED, false, rawStatus, rawBody);
    }

    private NewApiTaskStatus parseQuery(String taskId, HttpResponse<String> response) {
        int httpStatus = response.statusCode();
        String rawBody = truncate(response.body());
        String rawStatus = String.valueOf(httpStatus);
        if (httpStatus >= 200 && httpStatus < 300) {
            JsonNode node = parse(rawBody);
            if (node == null) {
                return NewApiTaskStatus.failed(taskId, null, WorkerErrorCodes.GATEWAY_INVALID_RESPONSE,
                        "invalid json", rawStatus, rawBody);
            }
            String status = text(node, "status");
            if (status == null) {
                status = text(node.path("data"), "status");
            }
            String outputUrl = outputUrl(node);
            String normalized = status == null ? "" : status.toUpperCase(Locale.ROOT);
            if (normalized.startsWith("FAILED") || normalized.startsWith("FAILURE")
                    || normalized.startsWith("ERROR") || normalized.startsWith("CANCELLED")) {
                return NewApiTaskStatus.failed(taskId, status, WorkerErrorCodes.UPSTREAM_VIDEO_FAILED,
                        status, rawStatus, rawBody);
            }
            if (normalized.startsWith("SUCCEEDED") || normalized.startsWith("SUCCESS")
                    || normalized.startsWith("COMPLETED")) {
                if (outputUrl == null) {
                    return NewApiTaskStatus.failed(taskId, status, WorkerErrorCodes.GATEWAY_INVALID_RESPONSE,
                            "success without output url", rawStatus, rawBody);
                }
                return NewApiTaskStatus.succeeded(taskId, status, outputUrl, rawStatus, rawBody);
            }
            if (normalized.isEmpty() && outputUrl != null) {
                return NewApiTaskStatus.succeeded(taskId, status, outputUrl, rawStatus, rawBody);
            }
            if (normalized.startsWith("RUNNING") || normalized.startsWith("QUEUED")
                    || normalized.startsWith("PENDING") || normalized.startsWith("PROCESSING")) {
                return NewApiTaskStatus.running(taskId, status, rawStatus, rawBody);
            }
            log.warn("newapi query unknown status taskId={} status={}", taskId, status);
            return NewApiTaskStatus.transientFailure(taskId, status, "unknown status", rawStatus, rawBody);
        }
        if (httpStatus == 429 || httpStatus >= 500) {
            return NewApiTaskStatus.transientFailure(taskId, rawStatus, "upstream unavailable", rawStatus, rawBody);
        }
        if (httpStatus == 401 || httpStatus == 403) {
            return NewApiTaskStatus.failed(taskId, rawStatus, WorkerErrorCodes.PROVIDER_UNAVAILABLE,
                    "auth failure", rawStatus, rawBody);
        }
        if (httpStatus == 400 && rawBody.contains("task_not_exist")) {
            return NewApiTaskStatus.failed(taskId, rawStatus, WorkerErrorCodes.GATEWAY_INVALID_RESPONSE,
                    "task not exist", rawStatus, rawBody);
        }
        return NewApiTaskStatus.failed(taskId, rawStatus, WorkerErrorCodes.JOB_FAILED,
                "newapi query failed", rawStatus, rawBody);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private static String outputUrl(JsonNode node) {
        String[] pointers = {"/object", "/result/url", "/result/video_url", "/url", "/video_url",
                "/data/url", "/data/0/url", "/output/video_url", "/content_url"};
        for (String pointer : pointers) {
            JsonNode value = node.at(pointer);
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_RAW_BODY_CHARS ? body : body.substring(0, MAX_RAW_BODY_CHARS);
    }
}
