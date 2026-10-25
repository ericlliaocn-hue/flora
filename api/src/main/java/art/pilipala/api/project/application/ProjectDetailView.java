package art.pilipala.api.project.application;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 项目详情（含画布快照）。
 */
public record ProjectDetailView(
        String id,
        String name,
        JsonNode canvasData,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
