package art.pilipala.worker.job.application;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import art.pilipala.worker.gateway.AnytokenAttemptResult;
import art.pilipala.worker.gateway.newapi.NewApiSubmitResult;
import art.pilipala.worker.gateway.newapi.NewApiTaskStatus;
import art.pilipala.worker.gateway.newapi.NewApiVideoClient;
import art.pilipala.worker.job.domain.ClaimedAttempt;
import art.pilipala.worker.job.domain.PollingAttempt;
import art.pilipala.worker.job.domain.WorkerErrorCodes;
import art.pilipala.worker.storage.TosObjectStorage;
import org.springframework.stereotype.Service;

/**
 * NewAPI Phase 2 视频网关：submit → NewAPI task id → query → content → TOS。
 *
 * <p>NewAPI 内部完成 channel 选择、retry、fallback；worker 只轮询公开 task id，
 * 不再持有 target 级 fallback 逻辑。内容下载走 NewAPI 的受保护 content 端点。
 */
@Service
public class NewApiVideoTaskGateway implements VideoTaskGateway {

    private final NewApiVideoClient client;
    private final TosObjectStorage tos;
    private final ObjectMapper mapper;

    public NewApiVideoTaskGateway(NewApiVideoClient client, TosObjectStorage tos, ObjectMapper mapper) {
        this.client = client;
        this.tos = tos;
        this.mapper = mapper;
    }

    @Override
    public NewApiSubmitResult submit(ClaimedAttempt attempt) {
        return client.submit(NewApiVideoRequests.build(attempt.paramsJson(), attempt.gatewayModel(),
                attempt.gatewayTimeoutSeconds(), attempt, mapper));
    }

    @Override
    public NewApiTaskStatus query(PollingAttempt attempt) {
        return client.query(attempt.upstreamTaskId());
    }

    @Override
    public AnytokenAttemptResult store(PollingAttempt attempt, NewApiTaskStatus status) {
        try {
            byte[] bytes = client.content(attempt.upstreamTaskId());
            String objectKey = "newapi/vid/" + attempt.jobId() + "/" + attempt.attempt() + ".mp4";
            tos.putObject(objectKey, bytes, "video/mp4");
            return AnytokenAttemptResult.success(List.of(objectKey), attempt.upstreamTaskId(),
                    status.rawStatus(), status.rawBody());
        } catch (Exception e) {
            // 下载/TOS 失败只重试 content，不重新提交上游任务。
            return AnytokenAttemptResult.failure(WorkerErrorCodes.PROVIDER_UNAVAILABLE, true,
                    attempt.upstreamTaskId(), "DOWNLOAD", status.rawBody());
        }
    }
}
