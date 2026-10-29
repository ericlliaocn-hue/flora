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
 * NewAPI 视频网关的 HTTP 客户端（WP21 Phase 2：OpenAI video 协议）。
 *
 * <p>只负责 submit/query/content 与把上游形状归一成 {@link NewApiSubmitResult} /
 * {@link NewApiTaskStatus}；不写 TOS、不改 job 状态、不做 fallback。密钥只从
 * {@link NewApiProperties} 读，不写日志。
 *
 * <p>Phase 2 协议：
 * <ul>
 *   <li>提交：{@code POST /v1/videos}，body 至少 model/prompt/seconds；</li>
 *   <li>查询：{@code GET /v1/videos/{id}}，状态 queued/in_progress/completed/failed；</li>
 *   <li>内容：{@code GET /v1/videos/{id}/content}，需要 service token；</li>
 *   <li>NewAPI 内部完成 channel fallback，客户端只看到最终 task 状态。</li>
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
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.videosUrl()))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(submitBody(request)));
            if (request.requestId() != null && !request.requestId().isBlank()) {
                builder.header("X-Pilipala-Request-Id", request.requestId());
            }
            if (request.deadlineAtEpochMs() > 0) {
                builder.header("X-Pilipala-Deadline-At", String.valueOf(request.deadlineAtEpochMs()));
            }
            httpRequest = builder.build();
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
            return NewApiTaskStatus.failed(taskId, null, WorkerErrorCodes.NO_AVAILABLE_CHANNEL,
                    "newapi not configured", "CONFIG", "");
        }
        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder(URI.create(properties.taskUrl(taskId)))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            return NewApiTaskStatus.failed(taskId, null, WorkerErrorCodes.NO_AVAILABLE_CHANNEL,
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

    @Override
    public byte[] content(String taskId) throws IOException {
        if (!properties.configured()) {
            throw new IOException("newapi not configured");
        }
        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder(URI.create(properties.contentUrl(taskId)))
                    .timeout(Duration.ofMillis(properties.timeoutMs()))
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .GET()
                    .build();
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid newapi base url", e);
        }
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("newapi content http " + response.statusCode());
        }
        return response.body();
    }

    private String submitBody(NewApiVideoRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", request.model());
        body.put("prompt", request.prompt());
        if (request.durationSeconds() > 0) {
            body.put("seconds", request.durationSeconds());
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
        log.warn("newapi submit rejected httpStatus={}", httpStatus);
        return NewApiSubmitResult.failure(WorkerErrorCodes.NO_AVAILABLE_CHANNEL, false, rawStatus, rawBody);
    }

    private NewApiTaskStatus parseQuery(String taskId, HttpResponse<String> response) {
        int httpStatus = response.statusCode();
        String rawBody = truncate(response.body());
        String rawStatus = String.valueOf(httpStatus);
        if (httpStatus >= 200 && httpStatus < 300) {
            JsonNode node = parse(rawBody);
            if (node == null) {
                return NewApiTaskStatus.failed(taskId, null, WorkerErrorCodes.NO_AVAILABLE_CHANNEL,
                        "invalid json", rawStatus, rawBody);
            }
            JsonNode payload = node.hasNonNull("data") && node.path("data").isObject() ? node.path("data") : node;
            String status = text(payload, "status");
            String normalized = status == null ? "" : status.toUpperCase(Locale.ROOT);
            if (normalized.startsWith("COMPLETED") || normalized.startsWith("SUCCEEDED")
                    || normalized.startsWith("SUCCESS")) {
                return NewApiTaskStatus.succeeded(taskId, status, null, rawStatus, rawBody);
            }
            if (normalized.startsWith("FAILED") || normalized.startsWith("FAILURE")
                    || normalized.startsWith("ERROR") || normalized.startsWith("CANCELLED")) {
                return NewApiTaskStatus.failed(taskId, status, WorkerErrorCodes.NO_AVAILABLE_CHANNEL,
                        failureMessage(payload, status), rawStatus, rawBody);
            }
            if (normalized.startsWith("QUEUED") || normalized.startsWith("RUNNING")
                    || normalized.startsWith("IN_PROGRESS") || normalized.startsWith("PROCESSING")
                    || normalized.startsWith("PENDING") || normalized.startsWith("SUBMITTED")) {
                return NewApiTaskStatus.running(taskId, status, rawStatus, rawBody);
            }
            log.warn("newapi query unknown status taskId={} status={}", taskId, status);
            return NewApiTaskStatus.transientFailure(taskId, status, "unknown status", rawStatus, rawBody);
        }
        if (httpStatus == 429 || httpStatus >= 500) {
            return NewApiTaskStatus.transientFailure(taskId, rawStatus, "upstream unavailable", rawStatus, rawBody);
        }
        if (httpStatus == 404) {
            return NewApiTaskStatus.failed(taskId, rawStatus, WorkerErrorCodes.NO_AVAILABLE_CHANNEL,
                    "task not found", rawStatus, rawBody);
        }
        return NewApiTaskStatus.failed(taskId, rawStatus, WorkerErrorCodes.NO_AVAILABLE_CHANNEL,
                "newapi query failed", rawStatus, rawBody);
    }

    private static String failureMessage(JsonNode node, String fallback) {
        String message = text(node.path("error"), "message");
        if (message != null) {
            return message;
        }
        message = text(node, "message", "fail_reason");
        return message == null ? fallback : message;
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

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_RAW_BODY_CHARS ? body : body.substring(0, MAX_RAW_BODY_CHARS);
    }
}
