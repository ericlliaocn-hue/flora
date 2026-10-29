package art.pilipala.worker.job.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.worker.gateway.AnytokenAttemptResult;
import art.pilipala.worker.gateway.newapi.NewApiSubmitResult;
import art.pilipala.worker.gateway.newapi.NewApiTaskStatus;
import art.pilipala.worker.gateway.newapi.NewApiVideoClient;
import art.pilipala.worker.gateway.newapi.NewApiVideoRequest;
import art.pilipala.worker.job.domain.ClaimedAttempt;
import art.pilipala.worker.job.domain.PollingAttempt;
import art.pilipala.worker.storage.TosObjectStorage;
import org.junit.jupiter.api.Test;

class NewApiVideoTaskGatewayTest {

    private final UUID jobId = UUID.randomUUID();
    private final UUID stepId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private final FakeClient client = new FakeClient();
    private final FakeTos tos = new FakeTos();

    @Test
    void submitBuildsTheRequestFromCanonicalParams() {
        client.submitResult = NewApiSubmitResult.accepted("task-1", "RUNNING", "200", "{}");

        NewApiSubmitResult result = gateway().submit(claimed("{\"prompt\":\"hi\",\"duration\":4,\"aspect_ratio\":\"16:9\"}"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.taskId()).isEqualTo("task-1");
        assertThat(client.lastRequest.model()).isEqualTo("sd-2-c4");
        assertThat(client.lastRequest.prompt()).isEqualTo("hi");
        assertThat(client.lastRequest.durationSeconds()).isEqualTo(4);
        assertThat(client.lastRequest.size()).isEqualTo("1280x720");
    }

    @Test
    void submitRejectedIsPassedThrough() {
        client.submitResult = NewApiSubmitResult.failure("JOB_FAILED", false, "400", "{}");

        NewApiSubmitResult result = gateway().submit(claimed("{\"prompt\":\"hi\"}"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.errorCode()).isEqualTo("JOB_FAILED");
    }

    @Test
    void queryUsesTheUpstreamTaskId() {
        client.queryStatus = NewApiTaskStatus.succeeded("task-1", "SUCCEEDED", "https://cdn/v.mp4", "200", "{}");

        NewApiTaskStatus status = gateway().query(polling());

        assertThat(client.lastTaskId).isEqualTo("task-1");
        assertThat(status.succeeded()).isTrue();
        assertThat(status.outputUrl()).isEqualTo("https://cdn/v.mp4");
    }

    @Test
    void storeDownloadsAndWritesTos() {
        NewApiTaskStatus status = NewApiTaskStatus.succeeded("task-1", "SUCCEEDED", "https://cdn/v.mp4", "200", "{}");

        AnytokenAttemptResult result = gateway().store(polling(), status);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.outputObjectKeys()).containsExactly("newapi/vid/" + jobId + "/1.mp4");
        assertThat(client.contentCalls).isEqualTo(1);
        assertThat(tos.keys).containsExactly("newapi/vid/" + jobId + "/1.mp4");
        assertThat(tos.contentTypes).containsExactly("video/mp4");
        assertThat(tos.bytes).hasSize(1);
    }

    @Test
    void storeDownloadFailureIsRetryableAndDoesNotWriteTos() {
        client.contentFailure = new IOException("download failed");
        NewApiTaskStatus status = NewApiTaskStatus.succeeded("task-1", "SUCCEEDED", "https://cdn/v.mp4", "200", "{}");

        AnytokenAttemptResult result = gateway().store(polling(), status);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(result.retryable()).isTrue();
        assertThat(tos.keys).isEmpty();
    }

    @Test
    void sizeForRatioMapsTheSupportedRatios() {
        assertThat(NewApiVideoRequests.sizeForRatio("16:9")).isEqualTo("1280x720");
        assertThat(NewApiVideoRequests.sizeForRatio("9:16")).isEqualTo("720x1280");
        assertThat(NewApiVideoRequests.sizeForRatio("1:1")).isEqualTo("1024x1024");
        assertThat(NewApiVideoRequests.sizeForRatio("21:9")).isEmpty();
    }

    private NewApiVideoTaskGateway gateway() {
        return new NewApiVideoTaskGateway(client, tos, new ObjectMapper());
    }

    private ClaimedAttempt claimed(String paramsJson) {
        return new ClaimedAttempt(jobId, stepId, 1, 3, "video.generate", "video-stable", UUID.randomUUID(),
                targetId, "default", "sd-2-c4", 900, paramsJson);
    }

    private PollingAttempt polling() {
        return new PollingAttempt(jobId, stepId, 1, 3, "video.generate", "video-stable", targetId,
                UUID.randomUUID(), "task-1", 0, "{\"prompt\":\"hi\"}");
    }


    private static final class FakeClient implements NewApiVideoClient {
        private NewApiSubmitResult submitResult;
        private NewApiTaskStatus queryStatus;
        private NewApiVideoRequest lastRequest;
        private String lastTaskId;
        private int contentCalls;
        private IOException contentFailure;

        @Override
        public NewApiSubmitResult submit(NewApiVideoRequest request) {
            lastRequest = request;
            return submitResult;
        }

        @Override
        public NewApiTaskStatus query(String taskId) {
            lastTaskId = taskId;
            return queryStatus;
        }

        @Override
        public byte[] content(String taskId) throws IOException {
            contentCalls++;
            if (contentFailure != null) {
                throw contentFailure;
            }
            return "mp4-bytes".getBytes();
        }
    }

    private static final class FakeTos implements TosObjectStorage {
        private final List<String> keys = new ArrayList<>();
        private final List<byte[]> bytes = new ArrayList<>();
        private final List<String> contentTypes = new ArrayList<>();

        @Override
        public void putObject(String objectKey, byte[] content, String contentType) {
            keys.add(objectKey);
            bytes.add(content);
            contentTypes.add(contentType);
        }
    }
}
