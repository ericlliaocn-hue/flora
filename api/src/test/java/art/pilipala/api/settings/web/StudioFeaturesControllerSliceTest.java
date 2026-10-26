package art.pilipala.api.settings.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import art.pilipala.api.auth.infra.SaTokenConfig;
import art.pilipala.api.settings.application.StudioFeaturesService;
import art.pilipala.api.settings.domain.StudioRuntimeKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = StudioFeaturesController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SaTokenConfig.class))
class StudioFeaturesControllerSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudioFeaturesService studioFeaturesService;

    @Test
    void featuresReturnsSnakeCaseEnvelope() throws Exception {
        when(studioFeaturesService.features()).thenReturn(new StudioFeaturesService.StudioFeaturesView(
                true,
                StudioRuntimeKeys.DEFAULT_PROMO_END_AT,
                StudioRuntimeKeys.DEFAULT_PROMO_TITLE,
                StudioRuntimeKeys.DEFAULT_PROMO_COPY,
                StudioRuntimeKeys.DEFAULT_PROMO_CTA,
                true,
                StudioRuntimeKeys.DEFAULT_MEMBERSHIP_BADGE));

        mockMvc.perform(get("/api/v1/studio/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.error_code").doesNotExist())
                .andExpect(jsonPath("$.data.promo_enabled").value(true))
                .andExpect(jsonPath("$.data.promo_end_at").value(StudioRuntimeKeys.DEFAULT_PROMO_END_AT))
                .andExpect(jsonPath("$.data.promo_title").value(StudioRuntimeKeys.DEFAULT_PROMO_TITLE))
                .andExpect(jsonPath("$.data.promo_copy").value(StudioRuntimeKeys.DEFAULT_PROMO_COPY))
                .andExpect(jsonPath("$.data.promo_cta").value(StudioRuntimeKeys.DEFAULT_PROMO_CTA))
                .andExpect(jsonPath("$.data.membership_enabled").value(true))
                .andExpect(jsonPath("$.data.membership_badge").value(StudioRuntimeKeys.DEFAULT_MEMBERSHIP_BADGE));
    }
}
