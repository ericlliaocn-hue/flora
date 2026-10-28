package art.pilipala.worker.job.application;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.worker.gateway.AnytokenAttemptResult;
import art.pilipala.worker.gateway.MediaDownloader;
import art.pilipala.worker.gateway.newapi.NewApiSubmitResult;
import art.pilipala.worker.gateway.newapi.NewApiTaskStatus;
import art.pilipala.worker.gateway.newapi.NewApiVideoClient;
import art.pilipala.worker.job.domain.ClaimedAttempt;
import art.pilipala.worker.job.domain.PollingAttempt;
import art.pilipala.worker.job.domain.WorkerErrorCodes;
import art.pilipala.worker.job.domain.WorkerTarget;
import art.pilipala.worker.storage.TosObjectStorage;
import org.springframework.stereotype.Service;

/** NewAPI 视频的 submit/query/store 实现；TOS 写入只发生在 store。 */
@Service
public class NewApiVideoTaskGateway implements VideoTaskGateway {

    private final NewApiVideoClient client;
    private final MediaDownloader downloader;
    private final TosObjectStorage tos;
    private final ObjectMapper mapper;

    public NewApiVideoTaskGateway(NewApiVideoClient client, MediaDownloader downloader, TosObjectStorage tos,
            ObjectMapper mapper) {
        this.client = client;
        this.downloader = downloader;
        this.tos = tos;
        this.mapper = mapper;
    }

    @Override
    public NewApiSubmitResult submit(ClaimedAttempt attempt, WorkerTarget target) {
        return client.submit(NewApiVideoRequests.build(attempt.paramsJson(), target, mapper));
    }

    @Override
    public NewApiTaskStatus query(PollingAttempt attempt, WorkerTarget target) {
        return client.query(attempt.upstreamTaskId());
    }

    @Override
    public AnytokenAttemptResult store(PollingAttempt attempt, NewApiTaskStatus status) {
        try {
            byte[] bytes = downloader.download(status.outputUrl());
            String objectKey = "newapi/vid/" + attempt.jobId() + "/" + attempt.attempt() + ".mp4";
            tos.putObject(objectKey, bytes, "video/mp4");
            return AnytokenAttemptResult.success(List.of(objectKey), attempt.upstreamTaskId(),
                    status.rawStatus(), status.rawBody());
        } catch (Exception e) {
            // 下载/TOS 失败只重试 store，不重新提交上游任务。
            return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true,
                    attempt.upstreamTaskId(), "DOWNLOAD", status.rawBody());
        }
    }
}
