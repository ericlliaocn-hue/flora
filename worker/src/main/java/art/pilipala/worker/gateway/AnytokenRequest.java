package art.pilipala.worker.gateway;

import java.util.UUID;

/**
 * 调上游的唯一入参。
 *
 * @param jobId      服务端 job id
 * @param attempt    第几次 attempt（从 1 开始）
 * @param capability 能力名，如 image.generate
 * @param model      pilipala 内部模型名
 * @param paramsJson 已物化的参数（JSON 文本）
 */
public record AnytokenRequest(
        UUID jobId,
        int attempt,
        String capability,
        String model,
        String paramsJson) {
}
