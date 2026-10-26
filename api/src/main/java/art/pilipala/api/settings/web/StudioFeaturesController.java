package art.pilipala.api.settings.web;

import art.pilipala.api.common.ApiResponse;
import art.pilipala.api.settings.application.StudioFeaturesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/studio")
public class StudioFeaturesController {

    private final StudioFeaturesService studioFeaturesService;

    public StudioFeaturesController(StudioFeaturesService studioFeaturesService) {
        this.studioFeaturesService = studioFeaturesService;
    }

    @GetMapping("/features")
    public ApiResponse<StudioFeaturesService.StudioFeaturesView> features() {
        return ApiResponse.ok(studioFeaturesService.features());
    }
}
