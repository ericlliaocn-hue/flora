package com.pilipala.api.i18n.infra;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.pilipala.api.i18n.domain.I18nMessage;

@Mapper
public interface I18nMessageMapper {

    String COLS = "id, key, locale, template, updated_at";

    @Select("SELECT " + COLS + " FROM i18n_message WHERE key = #{key} AND locale = #{locale}")
    I18nMessage findByKeyAndLocale(@Param("key") String key, @Param("locale") String locale);

    @Select("SELECT " + COLS + " FROM i18n_message WHERE key = #{key} ORDER BY locale")
    List<I18nMessage> findByKey(@Param("key") String key);

    @Select("INSERT INTO i18n_message (key, locale, template, updated_at) VALUES (#{key}, #{locale}, #{template}, now()) "
            + "ON CONFLICT (key, locale) DO UPDATE SET template = EXCLUDED.template, updated_at = now() "
            + "RETURNING " + COLS)
    I18nMessage upsert(@Param("key") String key, @Param("locale") String locale, @Param("template") String template);

    @Select("SELECT " + COLS + " FROM i18n_message ORDER BY key, locale")
    List<I18nMessage> searchAll();

    @Select("SELECT " + COLS + " FROM i18n_message WHERE key ILIKE #{pattern} OR template ILIKE #{pattern} "
            + "ORDER BY key, locale")
    List<I18nMessage> searchByKeyword(@Param("pattern") String pattern);
}
