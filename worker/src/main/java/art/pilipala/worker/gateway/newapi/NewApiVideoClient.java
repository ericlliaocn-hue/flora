package art.pilipala.worker.gateway.newapi;

/**
 * NewAPI 视频网关客户端。实现不得把异常抛给调用方：网络/HTTP/协议失败都归一成结果。
 */
public interface NewApiVideoClient {

    NewApiSubmitResult submit(NewApiVideoRequest request);

    NewApiTaskStatus query(String taskId);
}
