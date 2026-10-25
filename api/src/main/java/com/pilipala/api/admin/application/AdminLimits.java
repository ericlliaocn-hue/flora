package com.pilipala.api.admin.application;

/**
 * 运维后台列表参数的收敛规则。
 *
 * <p>{@code limit} 一律收敛到 {@code 1..200}：不合法值不报错，而是夹到区间内，
 * 避免运营手滑传了 0 或 100000 拿到空页或全表。
 */
public final class AdminLimits {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    private AdminLimits() {
    }

    /** 把请求里的 limit 夹到 1..MAX_LIMIT。 */
    public static int limit(int requested) {
        if (requested <= 0) {
            return 1;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
