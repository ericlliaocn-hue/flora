package art.pilipala.api.project.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * canvas_project 行。列与 docs/engineering/data-model/projects.md 一致。
 *
 * @param id            主键
 * @param userId        所属用户；只允许本人读写删
 * @param name          项目名称
 * @param canvasDataJson 画布快照（JSON 文本）
 * @param version       乐观递增版本号，每次更新 +1
 * @param createdAt     创建时间
 * @param updatedAt     最后保存时间
 */
public record CanvasProject(
        UUID id,
        UUID userId,
        String name,
        String canvasDataJson,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
