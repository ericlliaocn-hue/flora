package art.pilipala.worker.job.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.worker.gateway.AnytokenAttemptResult;
import art.pilipala.worker.gateway.MediaDownloader;
import art.pilipala.worker.gateway.newapi.NewApiSubmitResult;
import art.pilipala.worker.gateway.newapi.NewApiTaskStatus;
import art.pilipala.worker.gateway.newapi.NewApiVideoClient;
import art.pilipala.worker.gateway.newapi.NewApiVideoRequest;
import art.pilipala.worker.job.domain.ClaimedAttempt;
import art.pilipala.worker.job.domain.WorkerTarget;
import art.pilipala.worker.storage.TosObjectStorage;
import org.junit.jupiter.api.Test;

class NewApiVideoExecutorTest {

    private final UUID jobId = UUID.randomUUID();
    private final UUID stepId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();
    private final FakeClient client = new FakeClient();
    private final FakeDownloader downloader = new FakeDownloader();
    private final FakeTos tos = new FakeTos();

    @Test
    void submitThenQuerySuccessStoresTheVideoInTos() {
        client.submitResult = NewApiSubmitResult.accepted("task-1", "RUNNING", "200", "{}");
        client.statuses.add(NewApiTaskStatus.succeeded("task-1", "SUCCEEDED", "https://cdn/v.mp4", "200", "{}"));

        AnytokenAttemptResult result = executor(5).execute(attempt(), target(5));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.outputObjectKeys()).containsExactly("newapi/vid/" + jobId + "/1.mp4");
        assertThat(tos.keys).containsExactly("newapi/vid/" + jobId + "/1.mp4");
        assertThat(tos.contentTypes).containsExactly("video/mp4");
        assertThat(downloader.urls).containsExactly("https://cdn/v.mp4");
        assertThat(tos.bytes).hasSize(1);
        assertThat(new String(tos.bytes.getFirst())).isEqualTo("mp4-bytes");
    }

    @Test
    void submitRejectedDoesNotQuery() {
        client.submitResult = NewApiSubmitResult.failure("JOB_FAILED", false, "400", "{}");

        AnytokenAttemptResult result = executor(5).execute(attempt(), target(5));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.errorCode()).isEqualTo("JOB_FAILED");
        assertThat(result.retryable()).isFalse();
        assertThat(client.queries).isZero();
    }

    @Test
    void terminalQueryFailureReturnsTheUpstreamErrorCode() {
        client.submitResult = NewApiSubmitResult.accepted("task-1", "RUNNING", "200", "{}");
        client.statuses.add(NewApiTaskStatus.failed("task-1", "FAILED: bad prompt",
                "UPSTREAM_VIDEO_FAILED", "bad prompt", "200", "{}"));

        AnytokenAttemptResult result = executor(5).execute(attempt(), target(5));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.errorCode()).isEqualTo("UPSTREAM_VIDEO_FAILED");
        assertThat(result.retryable()).isFalse();
        assertThat(client.queries).isEqualTo(1);
    }

    @Test
    void runningThenSuccessKeepsPolling() {
        client.submitResult = NewApiSubmitResult.accepted("task-1", "RUNNING", "200", "{}");
        client.statuses.add(NewApiTaskStatus.running("task-1", "RUNNING", "200", "{}"));
        client.statuses.add(NewApiTaskStatus.running("task-1", "RUNNING", "200", "{}"));
        client.statuses.add(NewApiTaskStatus.succeeded("task-1", "SUCCEEDED", "https://cdn/v.mp4", "200", "{}"));

        AnytokenAttemptResult result = executor(5).execute(attempt(), target(5));

        assertThat(result.succeeded()).isTrue();
        assertThat(client.queries).isEqualTo(3);
    }

    @Test
    void targetBudgetExhaustedReturnsGatewayTimeout() {
        client.submitResult = NewApiSubmitResult.accepted("task-1", "RUNNING", "200", "{}");
        client.alwaysRunning = true;

        AnytokenAttemptResult result = executor(200).execute(attempt(), target(1));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.errorCode()).isEqualTo("GATEWAY_TIMEOUT");
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void downloadFailureIsRetryableAndDoesNotWriteTos() {
        client.submitResult = NewApiSubmitResult.accepted("task-1", "RUNNING", "200", "{}");
        client.statuses.add(NewApiTaskStatus.succeeded("task-1", "SUCCEEDED", "https://cdn/v.mp4", "200", "{}"));
        downloader.failure = new IOException("download failed");

        AnytokenAttemptResult result = executor(5).execute(attempt(), target(5));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.errorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
        assertThat(result.retryable()).isTrue();
        assertThat(tos.keys).isEmpty();
    }

    @Test
    void sizeForRatioMapsTheSupportedRatios() {
        assertThat(NewApiVideoExecutor.sizeForRatio("16:9")).isEqualTo("1280x720");
        assertThat(NewApiVideoExecutor.sizeForRatio("9:16")).isEqualTo("720x1280");
        assertThat(NewApiVideoExecutor.sizeForRatio("1:1")).isEqualTo("1024x1024");
        assertThat(NewApiVideoExecutor.sizeForRatio("21:9")).isEmpty();
    }

    private NewApiVideoExecutor executor(long pollIntervalMs) {
        return new NewApiVideoExecutor(client, downloader, tos, new ObjectMapper(), pollIntervalMs);
    }

    private ClaimedAttempt attempt() {
        return new ClaimedAttempt(jobId, stepId, 1, 3, "video.generate", "video-stable", UUID.randomUUID(),
                targetId, "{\"prompt\":\"hi\",\"duration\":4,\"aspect_ratio\":\"16:9\"}");
    }

    private WorkerTarget target(int timeoutSeconds) {
        return new WorkerTarget(targetId, "newapi/sd-2-c4", "newapi", "sd-2-c4", timeoutSeconds, true);
    }

    private static final class FakeClient implements NewApiVideoClient {
        private NewApiSubmitResult submitResult;
        private final Deque<NewApiTaskStatus> statuses = new ArrayDeque<>();
        private boolean alwaysRunning;
        private int queries;

        @Override
        public NewApiSubmitResult submit(NewApiVideoRequest request) {
            return submitResult;
        }

        @Override
        public NewApiTaskStatus query(String taskId) {
            queries++;
            if (alwaysRunning) {
                return NewApiTaskStatus.running(taskId, "RUNNING", "200", "{}");
            }
            return statuses.isEmpty()
                    ? NewApiTaskStatus.running(taskId, "RUNNING", "200", "{}")
                    : statuses.poll();
        }
    }

    private static final class FakeDownloader implements MediaDownloader {
        private final List<String> urls = new ArrayList<>();
        private IOException failure;

        @Override
        public byte[] download(String url) throws IOException {
            urls.add(url);
            if (failure != null) {
                throw failure;
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
