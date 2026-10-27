package art.pilipala.api.settings.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;

import art.pilipala.api.settings.domain.StudioRuntimeKeys;
import art.pilipala.api.settings.infra.AppRuntimeSettingMapper;
import art.pilipala.api.settings.infra.AppRuntimeSettingMapper.AppRuntimeSettingRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudioFeaturesServiceTest {

    @Mock
    private AppRuntimeSettingMapper mapper;

    private StudioFeaturesService service;

    @BeforeEach
    void setUp() {
        service = new StudioFeaturesService(new RuntimeSettingService(mapper));
    }

    @Test
    void missingKeysUseBriefDefaults() {
        when(mapper.findByKey(anyString())).thenReturn(null);

        StudioFeaturesService.StudioFeaturesView view = service.features();

        assertThat(view.promoEnabled()).isTrue();
        assertThat(view.promoEndAt()).isEqualTo(StudioRuntimeKeys.DEFAULT_PROMO_END_AT);
        assertThat(view.promoTitle()).isEqualTo(StudioRuntimeKeys.DEFAULT_PROMO_TITLE);
        assertThat(view.promoCopy()).isEqualTo(StudioRuntimeKeys.DEFAULT_PROMO_COPY);
        assertThat(view.promoCta()).isEqualTo(StudioRuntimeKeys.DEFAULT_PROMO_CTA);
        assertThat(view.membershipEnabled()).isTrue();
        assertThat(view.membershipBadge()).isEqualTo(StudioRuntimeKeys.DEFAULT_MEMBERSHIP_BADGE);
        assertThat(view.enterpriseEnabled()).isFalse();
    }

    @Test
    void emptyMembershipBadgeIsPreserved() {
        when(mapper.findByKey(anyString())).thenReturn(null);
        when(mapper.findByKey(StudioRuntimeKeys.MEMBERSHIP_BADGE))
                .thenReturn(row(StudioRuntimeKeys.MEMBERSHIP_BADGE, ""));

        assertThat(service.features().membershipBadge()).isEmpty();
    }

    @Test
    void storedValuesOverrideDefaults() {
        when(mapper.findByKey(StudioRuntimeKeys.PROMO_ENABLED)).thenReturn(row(StudioRuntimeKeys.PROMO_ENABLED, "false"));
        when(mapper.findByKey(StudioRuntimeKeys.PROMO_END_AT)).thenReturn(row(StudioRuntimeKeys.PROMO_END_AT, "2026-12-01T00:00:00.000Z"));
        when(mapper.findByKey(StudioRuntimeKeys.PROMO_TITLE)).thenReturn(row(StudioRuntimeKeys.PROMO_TITLE, "自定义标题"));
        when(mapper.findByKey(StudioRuntimeKeys.PROMO_COPY)).thenReturn(row(StudioRuntimeKeys.PROMO_COPY, "自定义副文案"));
        when(mapper.findByKey(StudioRuntimeKeys.PROMO_CTA)).thenReturn(row(StudioRuntimeKeys.PROMO_CTA, "马上充值"));
        when(mapper.findByKey(StudioRuntimeKeys.MEMBERSHIP_ENABLED)).thenReturn(row(StudioRuntimeKeys.MEMBERSHIP_ENABLED, "true"));
        when(mapper.findByKey(StudioRuntimeKeys.MEMBERSHIP_BADGE)).thenReturn(row(StudioRuntimeKeys.MEMBERSHIP_BADGE, "限时 3 折"));

        StudioFeaturesService.StudioFeaturesView view = service.features();

        assertThat(view.promoEnabled()).isFalse();
        assertThat(view.promoEndAt()).isEqualTo("2026-12-01T00:00:00.000Z");
        assertThat(view.promoTitle()).isEqualTo("自定义标题");
        assertThat(view.promoCopy()).isEqualTo("自定义副文案");
        assertThat(view.promoCta()).isEqualTo("马上充值");
        assertThat(view.membershipEnabled()).isTrue();
        assertThat(view.membershipBadge()).isEqualTo("限时 3 折");
    }

    private static AppRuntimeSettingRow row(String key, String value) {
        return new AppRuntimeSettingRow(key, value, Instant.parse("2026-10-27T00:00:00Z"));
    }
}
