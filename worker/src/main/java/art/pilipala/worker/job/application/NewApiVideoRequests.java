package art.pilipala.worker.job.application;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.worker.gateway.newapi.NewApiVideoRequest;
import art.pilipala.worker.job.domain.ClaimedAttempt;
import art.pilipala.worker.job.domain.WorkerTarget;

/** canonical 参数 → NewAPI 提交体的最小映射；不按 model 名写分支。 */
final class NewApiVideoRequests {

    private NewApiVideoRequests() {
    }

    static NewApiVideoRequest build(String paramsJson, WorkerTarget target, ClaimedAttempt attempt,
            ObjectMapper mapper) {
        JsonNode params = read(paramsJson, mapper);
        String prompt = params.path("prompt").asText("");
        int duration = params.path("duration").asInt(params.path("duration_s").asInt(5));
        String size = params.path("size").asText("");
        if (size.isBlank()) {
            size = sizeForRatio(params.path("aspect_ratio").asText(""));
        }
        long deadline = Instant.now().plusSeconds(Math.max(1, target.timeoutSeconds())).toEpochMilli();
        String requestId = attempt.jobId() + ":" + attempt.attempt();
        return new NewApiVideoRequest(target.upstreamModel(), prompt, duration, size, paramsJson, requestId, deadline);
    }

    static JsonNode read(String paramsJson, ObjectMapper mapper) {
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
}
