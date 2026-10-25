package com.pilipala.api.project.infra;

import com.pilipala.api.common.ErrorCodes;

/**
 * 带稳定 error_code 的项目域异常。
 *
 * <p>异常本身只带 error_code，不带可出站的文案：短句统一由
 * {@code I18nMessageService} 按 locale 解析，禁止把 {@code e.getMessage()}
 * 直接回给客户端。
 */
public class ProjectException extends RuntimeException {

    private final String errorCode;

    private ProjectException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    /** 项目不存在，或不属于当前用户（不泄露存在性）。 */
    public static ProjectException projectNotFound() {
        return new ProjectException(ErrorCodes.PROJECT_NOT_FOUND);
    }

    /** 素材不存在，或不属于当前用户（不泄露存在性）。 */
    public static ProjectException assetNotFound() {
        return new ProjectException(ErrorCodes.ASSET_NOT_FOUND);
    }

    /** 请求字段不合法。 */
    public static ProjectException validation() {
        return new ProjectException(ErrorCodes.VALIDATION_ERROR);
    }

    public String errorCode() {
        return errorCode;
    }
}
