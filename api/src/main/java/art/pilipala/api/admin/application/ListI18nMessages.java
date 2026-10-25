package art.pilipala.api.admin.application;

import java.util.List;

import art.pilipala.api.i18n.infra.I18nMessageRepository;
import org.springframework.stereotype.Service;

/**
 * 用例：列出 i18n 词条（支持按关键字搜索）。
 *
 * <p>词条存在 {@code i18n_message} 表里，运维改完下一条请求就生效，不用重启 api。
 */
@Service
public class ListI18nMessages {

    private final I18nMessageRepository messages;

    public ListI18nMessages(I18nMessageRepository messages) {
        this.messages = messages;
    }

    /**
     * @param query 关键字；为空时列出全部
     */
    public List<I18nMessageView> list(String query) {
        return messages.search(query).stream()
                .map(I18nMessageView::of)
                .toList();
    }
}
