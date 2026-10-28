package art.pilipala.worker.job.application;

import art.pilipala.worker.gateway.AnytokenAttemptResult;
import art.pilipala.worker.job.domain.ClaimedAttempt;
import art.pilipala.worker.job.domain.WorkerTarget;

/** 视频 target 的执行端口；实现可以是同步 NewAPI，也可以是后续的 submit/query 拆步。 */
public interface VideoExecutor {

    AnytokenAttemptResult execute(ClaimedAttempt attempt, WorkerTarget target);
}
