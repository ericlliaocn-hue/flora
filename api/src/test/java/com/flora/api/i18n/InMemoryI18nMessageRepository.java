package com.flora.api.i18n;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.flora.api.i18n.domain.I18nMessage;
import com.flora.api.i18n.infra.I18nMessageRepository;

/**
 * 内存版 i18n_message 仓储：语义与 Jdbc 实现一致（唯一键 (key, locale)）。
 *
 * <p>单测里用它改模板，验证 C 端文案随「DB」动态变化。
 */
public class InMemoryI18nMessageRepository implements I18nMessageRepository {

    private final Map<String, I18nMessage> rows = new LinkedHashMap<>();

    @Override
    public Optional<I18nMessage> findByKeyAndLocale(String key, String locale) {
        return Optional.ofNullable(rows.get(index(key, locale)));
    }

    @Override
    public List<I18nMessage> findByKey(String key) {
        List<I18nMessage> found = new ArrayList<>();
        rows.values().stream().filter(row -> row.key().equals(key)).forEach(found::add);
        return found;
    }

    @Override
    public I18nMessage upsert(String key, String locale, String template) {
        I18nMessage message = new I18nMessage(UUID.randomUUID(), key, locale, template, Instant.now());
        rows.put(index(key, locale), message);
        return message;
    }

    /** 删掉一条，模拟运维误删词条后的回退路径。 */
    public void delete(String key, String locale) {
        rows.remove(index(key, locale));
    }

    @Override
    public List<I18nMessage> search(String query) {
        String keyword = query == null ? "" : query.trim().toLowerCase();
        return rows.values().stream()
                .filter(row -> keyword.isEmpty()
                        || row.key().toLowerCase().contains(keyword)
                        || row.template().toLowerCase().contains(keyword))
                .sorted(Comparator.comparing(I18nMessage::key).thenComparing(I18nMessage::locale))
                .toList();
    }

    private String index(String key, String locale) {
        return key + "|" + locale;
    }
}
