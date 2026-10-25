package art.pilipala.api.admin.application;

import java.util.Set;

import art.pilipala.api.i18n.infra.I18nMessageRepository;
import art.pilipala.api.job.infra.JobException;
import org.springframework.stereotype.Service;

/**
 * 用例：在线改一条文案词条（写入即生效）。
 *
 * <p>写的是 {@code i18n_message} 表，{@code I18nMessageService} 每次解析都查表，
 * 所以改完下一次触发对应错误码就是新文案。
 */
@Service
public class UpsertI18nMessage {

    private static final Set<String> LOCALES = Set.of("zh", "en");
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_TEMPLATE_LENGTH = 1000;

    private final I18nMessageRepository messages;

    public UpsertI18nMessage(I18nMessageRepository messages) {
        this.messages = messages;
    }

    /**
     * 新增或覆盖一条词条。
     *
     * @param key 稳定键；去空白后长度 1..128
     * @param locale zh 或 en
     * @param template 短句模板；去空白后长度 1..1000
     */
    public I18nMessageView upsert(String key, String locale, String template) {
        String normalizedKey = key == null ? "" : key.trim();
        String normalizedLocale = locale == null ? "" : locale.trim();
        String normalizedTemplate = template == null ? "" : template.trim();
        if (normalizedKey.isEmpty() || normalizedKey.length() > MAX_KEY_LENGTH) {
            throw JobException.validation();
        }
        if (!LOCALES.contains(normalizedLocale)) {
            throw JobException.validation();
        }
        if (normalizedTemplate.isEmpty() || normalizedTemplate.length() > MAX_TEMPLATE_LENGTH) {
            throw JobException.validation();
        }
        return I18nMessageView.of(messages.upsert(normalizedKey, normalizedLocale, normalizedTemplate));
    }
}
