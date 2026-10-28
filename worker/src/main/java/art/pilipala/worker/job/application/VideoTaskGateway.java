package art.pilipala.worker.job.application;

import art.pilipala.worker.gateway.AnytokenAttemptResult;
import art.pilipala.worker.gateway.newapi.NewApiSubmitResult;
import art.pilipala.worker.gateway.newapi.NewApiTaskStatus;
import art.pilipala.worker.job.domain.ClaimedAttempt;
import art.pilipala.worker.job.domain.PollingAttempt;
import art.pilipala.worker.job.domain.WorkerTarget;

/**
 * 视频 target 的三段操作：submit / query / store。
 *
 * <p>拆步后由 worker 的 submit 和 query 两条路径分别调用；store 只负责下载 + TOS。
 */
public interface VideoTaskGateway {

    NewApiSubmitResult submit(ClaimedAttempt attempt, WorkerTarget target);

    NewApiTaskStatus query(PollingAttempt attempt, WorkerTarget target);

    AnytokenAttemptResult store(PollingAttempt attempt, NewApiTaskStatus status);
}
