package com.flora.api.admin.web;

import java.util.List;

import com.flora.api.admin.application.ListEnabledModels;
import com.flora.api.admin.application.ModelDisplayConfigView;
import com.flora.api.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端：当前上架的托管模型列表。
 *
 * <p>只返回 {@code enabled = true} 的展示配置，供前端渲染可选的生图模型。
 */
@RestController
@RequestMapping("/api/v1/models")
public class ModelController {

    private final ListEnabledModels models;

    public ModelController(ListEnabledModels models) {
        this.models = models;
    }

    @GetMapping
    public ApiResponse<List<ModelDisplayConfigView>> list() {
        return ApiResponse.ok(models.list());
    }
}
