package art.pilipala.api.admin.application;

import java.util.List;

import art.pilipala.api.admin.infra.ModelDisplayConfigRepository;
import org.springframework.stereotype.Service;

/**
 * 用例：列出全部模型展示配置（含已下架的），供运维后台管理。
 */
@Service
public class ListModelDisplayConfig {

    private final ModelDisplayConfigRepository models;

    public ListModelDisplayConfig(ModelDisplayConfigRepository models) {
        this.models = models;
    }

    public List<ModelDisplayConfigView> list() {
        return models.findAll().stream()
                .map(ModelDisplayConfigView::of)
                .toList();
    }
}
