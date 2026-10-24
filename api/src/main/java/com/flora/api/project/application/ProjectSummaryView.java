package com.flora.api.project.application;

import java.time.Instant;

/**
 * 项目摘要（列表用）。**不含** 画布大字段 {@code canvas_data}。
 */
public record ProjectSummaryView(
        String id,
        String name,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
