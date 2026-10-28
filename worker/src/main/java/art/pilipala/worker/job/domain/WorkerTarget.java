package art.pilipala.worker.job.domain;

import java.util.UUID;

/**
 * worker 看到的视频 target 最小视图（只读）。
 *
 * <p>不读渠道 Key、不读产品价；worker 只拿上游 model 与超时预算。
 */
public record WorkerTarget(
        UUID id,
        String code,
        String gateway,
        String upstreamModel,
        int timeoutSeconds,
        boolean enabled) {
}
