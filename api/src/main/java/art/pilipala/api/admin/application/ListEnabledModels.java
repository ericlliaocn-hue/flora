package art.pilipala.api.admin.application;

import java.util.List;

import art.pilipala.api.admin.infra.ModelDisplayConfigRepository;
import org.springframework.stereotype.Service;

/**
 * 用例：列出当前上架（enabled）的模型，供 C 端渲染托管生图模型列表。
 *
 * <p>这张表只管展示与上下架，绝不含供应商 API Key（ADR 0001）。
 */
@Service
public class ListEnabledModels {

    private final ModelDisplayConfigRepository models;

    public ListEnabledModels(ModelDisplayConfigRepository models) {
        this.models = models;
    }

    public List<ModelDisplayConfigView> list() {
        return models.findEnabled().stream()
                .map(ModelDisplayConfigView::of)
                .toList();
    }
}
