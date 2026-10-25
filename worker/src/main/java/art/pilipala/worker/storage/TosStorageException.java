package art.pilipala.worker.storage;

/**
 * 火山 TOS 写入失败。
 *
 * <p>只带脱敏后的错误摘要与 HTTP 状态码：**绝不**包含 access key / secret key /
 * Authorization 头（ADR 0005）。
 */
public class TosStorageException extends RuntimeException {

    private final int status;

    public TosStorageException(int status, String message) {
        super(message);
        this.status = status;
    }

    public TosStorageException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /** HTTP 状态码；网络层异常（超时 / IO 中断）时为 0。 */
    public int status() {
        return status;
    }
}
