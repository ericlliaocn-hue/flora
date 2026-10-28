package art.pilipala.worker.job.application;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.worker.gateway.AnytokenAttemptResult;
import art.pilipala.worker.gateway.MediaDownloader;
import art.pilipala.worker.gateway.newapi.NewApiSubmitResult;
import art.pilipala.worker.gateway.newapi.NewApiTaskStatus;
import art.pilipala.worker.gateway.newapi.NewApiVideoClient;
import art.pilipala.worker.gateway.newapi.NewApiVideoRequest;
import art.pilipala.worker.job.domain.ClaimedAttempt;
import art.pilipala.worker.job.domain.WorkerErrorCodes;
import art.pilipala.worker.job.domain.WorkerTarget;
import art.pilipala.worker.storage.TosObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 同步版 NewAPI 视频执行器：提交后按 target 预算轮询，成功后下载并写 TOS。
 *
 * <p>这是 Phase 1 的中间态：复用现有 claim → execute → complete 生命周期，
 * 先把 real NewAPI 路径打通；下一阶段改 submit/query 拆步，避免 worker 长时间持有租约。
 * 因此部署时要同时把 {@code PILIPALA_JOB_LEASE_TTL_SECONDS} 调到 ≥ target timeout。
 */
@Service
public class NewApiVideoExecutor implements VideoExecutor {

    private final NewApiVideoClient client;
    private final MediaDownloader downloader;
    private final TosObjectStorage tos;
    private final ObjectMapper mapper;
    private final long pollIntervalMs;

    public NewApiVideoExecutor(NewApiVideoClient client, MediaDownloader downloader, TosObjectStorage tos,
            ObjectMapper mapper,
            @Value("${pilipala.gateway.newapi.poll-interval-ms:2000}") long pollIntervalMs) {
        this.client = client;
        this.downloader = downloader;
        this.tos = tos;
        this.mapper = mapper;
        this.pollIntervalMs = Math.max(1, pollIntervalMs);
    }

    @Override
    public AnytokenAttemptResult execute(ClaimedAttempt attempt, WorkerTarget target) {
        NewApiSubmitResult submitted = client.submit(request(attempt.paramsJson(), target));
        if (!submitted.accepted()) {
            return AnytokenAttemptResult.failure(submitted.errorCode(), submitted.retryable(), null,
                    submitted.rawStatus(), submitted.rawBody());
        }

        String taskId = submitted.taskId();
        long deadline = System.nanoTime() + Math.max(1, target.timeoutSeconds()) * 1_000_000_000L;
        String lastRawStatus = submitted.rawStatus();
        String lastRawBody = submitted.rawBody();
        while (System.nanoTime() < deadline) {
            if (!sleep(pollIntervalMs)) {
                return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, taskId,
                        "INTERRUPTED", lastRawBody);
            }
            NewApiTaskStatus status = client.query(taskId);
            lastRawStatus = status.rawStatus();
            lastRawBody = status.rawBody();
            if (status.succeeded()) {
                return store(attempt, taskId, status);
            }
            if (status.terminal()) {
                return AnytokenAttemptResult.failure(status.errorCode(), status.retryable(), taskId,
                        lastRawStatus, lastRawBody);
            }
        }
        return AnytokenAttemptResult.failure(WorkerErrorCodes.GATEWAY_TIMEOUT, true, taskId, "TIMEOUT", lastRawBody);
    }

    private AnytokenAttemptResult store(ClaimedAttempt attempt, String taskId, NewApiTaskStatus status) {
        try {
            byte[] bytes = downloader.download(status.outputUrl());
            String objectKey = "newapi/vid/" + attempt.jobId() + "/" + attempt.attempt() + ".mp4";
            tos.putObject(objectKey, bytes, "video/mp4");
            return AnytokenAttemptResult.success(List.of(objectKey), taskId, status.rawStatus(), status.rawBody());
        } catch (Exception e) {
            // 同步版无法只重试下载；下一次 attempt 会重新提交。拆步后改成就地重试下载。
            return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true, taskId,
                    "DOWNLOAD", status.rawBody());
        }
    }

    private NewApiVideoRequest request(String paramsJson, WorkerTarget target) {
        JsonNode params = read(paramsJson);
        String prompt = params.path("prompt").asText("");
        int duration = params.path("duration").asInt(params.path("duration_s").asInt(5));
        String size = params.path("size").asText("");
        if (size.isBlank()) {
            size = sizeForRatio(params.path("aspect_ratio").asText(""));
        }
        return new NewApiVideoRequest(target.upstreamModel(), prompt, duration, size, paramsJson);
    }

    private JsonNode read(String paramsJson) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(paramsJson);
        } catch (JsonProcessingException e) {
            return mapper.createObjectNode();
        }
    }

    static String sizeForRatio(String ratio) {
        return switch (ratio == null ? "" : ratio.trim()) {
            case "16:9" -> "1280x720";
            case "9:16" -> "720x1280";
            case "1:1" -> "1024x1024";
            default -> "";
        };
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(Math.max(1, millis));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
