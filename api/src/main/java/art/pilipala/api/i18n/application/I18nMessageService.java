package art.pilipala.api.i18n.application;

import java.util.Map;
import java.util.Optional;

import art.pilipala.api.common.ErrorCodes;
import art.pilipala.api.i18n.domain.I18nMessage;
import art.pilipala.api.i18n.infra.I18nMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 词条解析：DB (key, locale) → DB (key, 'zh') → 内置种子 → key 本身。
 *
 * <p>查表失败不影响主流程：DB 异常只记 WARN 并回退到内置种子，
 * 绝不让「查文案」炸穿业务请求（ADR 0005）。
 */
@Service
public class I18nMessageService {

    private static final Logger log = LoggerFactory.getLogger(I18nMessageService.class);

    private final I18nMessageRepository messages;

    public I18nMessageService(I18nMessageRepository messages) {
        this.messages = messages;
    }

    /** 从 Accept-Language 头（或裸 locale）解析出 zh / en。 */
    public String localeOf(String acceptLanguage) {
        return I18nDefaultCatalog.localeOf(acceptLanguage);
    }

    public String resolve(String key, String locale) {
        return resolve(key, locale, Map.of());
    }

    /**
     * 取当前语言的友好短句。
     *
     * @param params 占位符参数；缺参不抛异常，保留原占位符
     */
    public String resolve(String key, String locale, Map<String, Object> params) {
        if (key == null || key.isBlank()) {
            // 空 key 没有可查的词条：按泛化码给一句友好话，绝不返回空串。
            return I18nDefaultCatalog.resolveDefault(ErrorCodes.PROVIDER_UNAVAILABLE, locale, params);
        }
        String lang = I18nDefaultCatalog.localeOf(locale);
        Optional<I18nMessage> found = find(key, lang);
        if (found.isEmpty() && "en".equals(lang)) {
            found = find(key, "zh");
        }
        return found
                .map(message -> I18nDefaultCatalog.interpolate(message.template(), params))
                .orElseGet(() -> I18nDefaultCatalog.resolveDefault(key, lang, params));
    }

    private Optional<I18nMessage> find(String key, String locale) {
        try {
            return messages.findByKeyAndLocale(key, locale);
        } catch (RuntimeException e) {
            log.warn("i18n_message 查询失败，回退内置种子 key={} locale={}", key, locale);
            return Optional.empty();
        }
    }
}
