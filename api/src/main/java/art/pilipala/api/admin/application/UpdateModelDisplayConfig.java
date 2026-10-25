package art.pilipala.api.admin.application;

import art.pilipala.api.admin.domain.ModelDisplayConfig;
import art.pilipala.api.admin.infra.ModelDisplayConfigRepository;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;

/**
 * 用例：改一条模型展示配置（上下架、改展示名、改排序、改能力）。
 *
 * <p>只影响前端展示：这里既不存也不读任何供应商密钥（ADR 0001）。
 */
@Service
public class UpdateModelDisplayConfig {

    private static final String DEFAULT_CAPABILITY = "image.generate";

    private final ModelDisplayConfigRepository models;

    public UpdateModelDisplayConfig(ModelDisplayConfigRepository models) {
        this.models = models;
    }

    /**
     * 新增或更新一行配置。
     *
     * @param model 模型名，同时是唯一键；为空按参数不合法处理
     * @param displayName 展示名；为空按参数不合法处理
     * @param capability 能力；为空时沿用已有值（没有则默认 image.generate）
     * @param enabled 是否上架；为 null 时沿用已有值（没有则默认上架）
     * @param sortOrder 排序位；为 null 时沿用已有值（没有则默认 0）
     */
    public ModelDisplayConfigView update(String model, String displayName, String capability, Boolean enabled,
            Integer sortOrder) {
        String normalizedModel = model == null ? "" : model.trim();
        String normalizedName = displayName == null ? "" : displayName.trim();
        if (normalizedModel.isEmpty() || normalizedName.isEmpty()) {
            throw JobException.validation();
        }
        ModelDisplayConfig existing = models.findByModel(normalizedModel).orElse(null);
        String normalizedCapability = capability == null ? "" : capability.trim();
        if (normalizedCapability.isEmpty()) {
            normalizedCapability = existing == null ? DEFAULT_CAPABILITY : existing.capability();
        }
        boolean resolvedEnabled = enabled == null ? (existing == null || existing.enabled()) : enabled;
        int resolvedSortOrder = sortOrder == null ? (existing == null ? 0 : existing.sortOrder()) : sortOrder;
        return ModelDisplayConfigView.of(models.upsert(normalizedModel, normalizedCapability, normalizedName,
                resolvedEnabled, resolvedSortOrder));
    }
}
