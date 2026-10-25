package art.pilipala.worker.gateway;

/**
 * 唯一的模型供应商适配器接口（ADR 0001）。
 *
 * <p>整个仓库只有这一个供应商入口：不允许 NewAPI、OpenAI 直连或第二个适配器。
 * 供应商差异全部收在 {@link AnytokenGatewayAdapter} 里，上层只认
 * {@link AnytokenAttemptResult}。
 */
public interface AnytokenGateway {

    /**
     * 跑一次 attempt。
     *
     * <p>实现不得抛异常给调用方：上游失败要归一成带 errorCode 的结果。
     */
    AnytokenAttemptResult execute(AnytokenRequest request);
}
