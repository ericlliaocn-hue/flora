package com.flora.api.project.domain;

import java.util.Locale;

/**
 * 云端素材来源。与 {@code user_asset.source} 的 CHECK 约束逐字一致。
 */
public enum AssetSource {

    /** 生图结果落进 TOS 后同步进来的素材。 */
    GENERATED("generated"),
    /** 用户上传 / 登记的素材。 */
    UPLOADED("uploaded");

    private final String value;

    AssetSource(String value) {
        this.value = value;
    }

    /** 小写形式，写 DB 与出 C 端都用它。 */
    public String value() {
        return value;
    }

    public static AssetSource of(String raw) {
        if (raw == null) {
            return GENERATED;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
