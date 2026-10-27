package art.pilipala.api.settings.application;

import art.pilipala.api.settings.domain.StudioRuntimeKeys;
import org.springframework.stereotype.Service;

/**
 * Studio 顶栏 / 活动条公开配置：读 {@code app_runtime_setting}，缺 key 用 brief 默认。
 */
@Service
public class StudioFeaturesService {

    private final RuntimeSettingService settings;

    public StudioFeaturesService(RuntimeSettingService settings) {
        this.settings = settings;
    }

    public StudioFeaturesView features() {
        return new StudioFeaturesView(
                settings.getBoolean(StudioRuntimeKeys.PROMO_ENABLED, true),
                settings.getString(StudioRuntimeKeys.PROMO_END_AT, StudioRuntimeKeys.DEFAULT_PROMO_END_AT),
                settings.getString(StudioRuntimeKeys.PROMO_TITLE, StudioRuntimeKeys.DEFAULT_PROMO_TITLE),
                settings.getString(StudioRuntimeKeys.PROMO_COPY, StudioRuntimeKeys.DEFAULT_PROMO_COPY),
                settings.getString(StudioRuntimeKeys.PROMO_CTA, StudioRuntimeKeys.DEFAULT_PROMO_CTA),
                settings.getBoolean(StudioRuntimeKeys.MEMBERSHIP_ENABLED, true),
                settings.getString(StudioRuntimeKeys.MEMBERSHIP_BADGE, StudioRuntimeKeys.DEFAULT_MEMBERSHIP_BADGE),
                settings.getBoolean(StudioRuntimeKeys.ENTERPRISE_ENABLED, false));
    }

    public record StudioFeaturesView(
            boolean promoEnabled,
            String promoEndAt,
            String promoTitle,
            String promoCopy,
            String promoCta,
            boolean membershipEnabled,
            String membershipBadge,
            boolean enterpriseEnabled) {
    }
}
