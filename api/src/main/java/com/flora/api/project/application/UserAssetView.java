package com.flora.api.project.application;

import java.time.Instant;

/**
 * 云端素材视图。
 *
 * @param signedUrl 现签的短期 TOS 读地址；对象键非法时为 null
 */
public record UserAssetView(
        String id,
        String projectId,
        String jobId,
        String source,
        String objectKey,
        String signedUrl,
        String mimeType,
        Instant createdAt) {
}
