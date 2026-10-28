package art.pilipala.worker.job.infra;

import java.util.Optional;
import java.util.UUID;

import art.pilipala.worker.job.domain.WorkerTarget;

/** worker 读取 target 的端口；只读，不写渠道/价格。 */
public interface WorkerTargetRepository {

    Optional<WorkerTarget> findById(UUID targetId);
}
