package art.pilipala.worker.storage;

/**
 * 火山 TOS 写入接口。
 *
 * <p>唯一实现是 {@link TosObjectStorageAdapter}（stub / http 双模式）；
 * 不建多云抽象，也不建第二套对象存储接口（TECH_STACK.md）。
 */
public interface TosObjectStorage {

    /**
     * 把对象字节写入 TOS。
     *
     * @param objectKey  对象键，例如 {@code anytoken/img/<job_id>/1.png}
     * @param content    对象字节，不允许为空
     * @param contentType 内容类型，例如 {@code image/png}
     * @throws TosStorageException 上传失败（已脱敏）
     */
    void putObject(String objectKey, byte[] content, String contentType);
}
