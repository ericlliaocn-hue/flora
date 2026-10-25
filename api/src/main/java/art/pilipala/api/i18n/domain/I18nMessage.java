package art.pilipala.api.i18n.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 一条文案模板。
 *
 * @param id        主键
 * @param key       稳定键，常与 error_code 相同
 * @param locale    zh / en
 * @param template  短句模板，可含 {placeholder}
 * @param updatedAt 最后修改时间
 */
public record I18nMessage(UUID id, String key, String locale, String template, Instant updatedAt) {
}
