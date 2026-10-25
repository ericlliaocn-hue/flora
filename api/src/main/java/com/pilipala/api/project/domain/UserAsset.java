package com.pilipala.api.project.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * user_asset 行。列与 docs/engineering/data-model/projects.md 一致。
 *
 * <p>只存 TOS 对象键，不存预签名 URL、不存图片字节（ADR 0003）。
 *
 * @param id        主键
 * @param userId    所属用户
 * @param projectId 关联画布项目，可为 null
 * @param jobId     关联生成任务，可为 null
 * @param source    来源：generated / uploaded
 * @param objectKey TOS 对象键
 * @param mimeType  MIME 类型
 * @param createdAt 创建时间
 */
public record UserAsset(
        UUID id,
        UUID userId,
        UUID projectId,
        UUID jobId,
        AssetSource source,
        String objectKey,
        String mimeType,
        Instant createdAt) {
}
