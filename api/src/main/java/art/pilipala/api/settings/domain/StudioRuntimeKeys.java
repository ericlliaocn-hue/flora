package art.pilipala.api.settings.domain;

/**
 * {@code app_runtime_setting} 中与 Studio 顶栏 / 活动条相关的已知 key 与缺省值。
 */
public final class StudioRuntimeKeys {

    public static final String PROMO_ENABLED = "studio.promo.enabled";
    public static final String PROMO_END_AT = "studio.promo.end_at";
    public static final String PROMO_TITLE = "studio.promo.title";
    public static final String PROMO_COPY = "studio.promo.copy";
    public static final String PROMO_CTA = "studio.promo.cta";
    public static final String MEMBERSHIP_BADGE = "studio.membership.badge";
    public static final String MEMBERSHIP_ENABLED = "studio.membership.enabled";
    public static final String ENTERPRISE_ENABLED = "studio.enterprise.enabled";

    public static final String DEFAULT_PROMO_END_AT = "2026-11-15T15:59:59.000Z";
    public static final String DEFAULT_PROMO_TITLE = "预付费加赠限时开启";
    public static final String DEFAULT_PROMO_COPY = "充值加赠最高 20%，任务成功才扣费";
    public static final String DEFAULT_PROMO_CTA = "限时抢购";
    public static final String DEFAULT_MEMBERSHIP_BADGE = "限时 4.6 折";

    private StudioRuntimeKeys() {
    }
}
