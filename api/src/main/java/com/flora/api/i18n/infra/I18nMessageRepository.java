package com.flora.api.i18n.infra;

import java.util.List;
import java.util.Optional;

import com.flora.api.i18n.domain.I18nMessage;

/**
 * i18n_message 表的仓储。SQL 只写在 {@link MybatisI18nMessageRepository} 里。
 */
public interface I18nMessageRepository {

    /** 精确取一条：(key, locale)。 */
    Optional<I18nMessage> findByKeyAndLocale(String key, String locale);

    /** 取某个 key 的全部语言版本。 */
    List<I18nMessage> findByKey(String key);

    /**
     * 写入或覆盖一条模板；供集成测试与后续 WP11 Admin 复用。
     */
    I18nMessage upsert(String key, String locale, String template);

    /**
     * 列出词条：按 {@code key ASC, locale ASC} 排序。
     *
     * @param query 非空时按 key 或 template 包含该关键字过滤
     */
    List<I18nMessage> search(String query);
}
