package com.flora.api.admin.application;

import java.time.Instant;
import java.util.UUID;

import com.flora.api.i18n.domain.I18nMessage;

/**
 * 运维后台看到的文案词条。
 */
public record I18nMessageView(
        UUID id,
        String key,
        String locale,
        String template,
        Instant updatedAt) {

    /** 领域对象 → 视图。 */
    public static I18nMessageView of(I18nMessage message) {
        return new I18nMessageView(message.id(), message.key(), message.locale(), message.template(),
                message.updatedAt());
    }
}
