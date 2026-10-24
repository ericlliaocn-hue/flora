package com.flora.api.job.infra;

import com.flora.api.common.ErrorCodes;

/**
 * 带稳定 error_code 的 Job 域异常。
 *
 * <p>异常本身只带 error_code，不带可出站的文案：短句统一由 {@link JobErrorMessages}
 * 按 locale 解析，禁止把 {@code e.getMessage()} 直接回给客户端。
 */
public class JobException extends RuntimeException {

    private final String errorCode;

    private JobException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    /** 任务不存在，或不属于当前用户（不泄露存在性）。 */
    public static JobException notFound() {
        return new JobException(ErrorCodes.JOB_NOT_FOUND);
    }

    /** 同一个幂等键对应了不同的请求体。 */
    public static JobException idempotencyConflict() {
        return new JobException(ErrorCodes.IDEMPOTENCY_CONFLICT);
    }

    /** 请求字段不合法，或撞上了非法状态迁移。 */
    public static JobException validation() {
        return new JobException(ErrorCodes.VALIDATION_ERROR);
    }

    /** 钱包冻结失败：余额不足。 */
    public static JobException walletInsufficient() {
        return new JobException(ErrorCodes.WALLET_INSUFFICIENT);
    }

    public String errorCode() {
        return errorCode;
    }
}
