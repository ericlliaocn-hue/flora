package art.pilipala.api.admin.application;

import java.time.Instant;
import java.util.UUID;

import art.pilipala.api.admin.domain.ModelDisplayConfig;

/**
 * 模型展示配置：只管前端展示与上下架，不含任何供应商密钥（ADR 0001）。
 */
public record ModelDisplayConfigView(
        UUID id,
        String model,
        String capability,
        String displayName,
        boolean enabled,
        int sortOrder,
        Instant updatedAt) {

    /** 领域对象 → 视图。 */
    public static ModelDisplayConfigView of(ModelDisplayConfig config) {
        return new ModelDisplayConfigView(config.id(), config.model(), config.capability(), config.displayName(),
                config.enabled(), config.sortOrder(), config.updatedAt());
    }
}
