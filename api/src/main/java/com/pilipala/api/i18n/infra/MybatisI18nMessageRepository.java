package com.pilipala.api.i18n.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.pilipala.api.i18n.domain.I18nMessage;

@Repository
public class MybatisI18nMessageRepository implements I18nMessageRepository {

    private final I18nMessageMapper mapper;

    public MybatisI18nMessageRepository(I18nMessageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<I18nMessage> findByKeyAndLocale(String key, String locale) {
        return Optional.ofNullable(mapper.findByKeyAndLocale(key, locale));
    }

    @Override
    public List<I18nMessage> findByKey(String key) {
        return mapper.findByKey(key);
    }

    @Override
    public I18nMessage upsert(String key, String locale, String template) {
        return mapper.upsert(key, locale, template);
    }

    @Override
    public List<I18nMessage> search(String query) {
        String keyword = query == null ? "" : query.trim();
        if (keyword.isEmpty()) {
            return mapper.searchAll();
        }
        return mapper.searchByKeyword("%" + keyword + "%");
    }
}
