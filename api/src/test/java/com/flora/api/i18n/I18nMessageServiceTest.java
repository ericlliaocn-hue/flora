package com.flora.api.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.flora.api.common.ErrorCodes;
import com.flora.api.i18n.application.I18nDefaultCatalog;
import com.flora.api.i18n.application.I18nMessageService;
import com.flora.api.i18n.domain.I18nMessage;
import com.flora.api.i18n.infra.I18nMessageRepository;
import org.junit.jupiter.api.Test;

/**
 * 词条回退链与安全插值：DB (key, locale) → DB (key, 'zh') → 内置种子 → key 本身。
 *
 * <p>任何一步都不许把异常原文漏给 C 端，也不许返回 null 或空串。
 */
class I18nMessageServiceTest {

    private final InMemoryI18nMessageRepository repository = new InMemoryI18nMessageRepository();
    private final I18nMessageService service = new I18nMessageService(repository);

    @Test
    void registeredCodesHaveChineseAndEnglishText() {
        assertThat(service.resolve(ErrorCodes.PROVIDER_UNAVAILABLE, "zh"))
                .isEqualTo("生成服务暂时不可用，请稍后重试。");
        assertThat(service.resolve(ErrorCodes.PROVIDER_UNAVAILABLE, "en"))
                .isEqualTo("The generation service is temporarily unavailable. Please try again later.");
    }

    @Test
    void everyRegisteredCodeHasText() {
        for (String code : new String[]{
                ErrorCodes.UNAUTHORIZED,
                ErrorCodes.VALIDATION_ERROR,
                ErrorCodes.JOB_NOT_FOUND,
                ErrorCodes.JOB_CANCELLED,
                ErrorCodes.IDEMPOTENCY_CONFLICT,
                ErrorCodes.JOB_FAILED,
                ErrorCodes.PROVIDER_UNAVAILABLE,
                ErrorCodes.WALLET_INSUFFICIENT}) {
            assertThat(service.resolve(code, "zh")).isNotEqualTo(code);
            assertThat(service.resolve(code, "en")).isNotEqualTo(code);
        }
    }

    @Test
    void theDatabaseTemplateWinsOverTheBuiltInSeed() {
        repository.upsert(ErrorCodes.WALLET_INSUFFICIENT, "zh", "请先充值再试。");

        assertThat(service.resolve(ErrorCodes.WALLET_INSUFFICIENT, "zh")).isEqualTo("请先充值再试。");
    }

    @Test
    void englishFallsBackToTheChineseDatabaseRow() {
        repository.upsert(ErrorCodes.JOB_NOT_FOUND, "zh", "找不到这个任务。");

        assertThat(service.resolve(ErrorCodes.JOB_NOT_FOUND, "en")).isEqualTo("找不到这个任务。");
    }

    @Test
    void missingRowsFallBackToTheBuiltInSeed() {
        repository.upsert(ErrorCodes.JOB_NOT_FOUND, "zh", "找不到这个任务。");
        repository.delete(ErrorCodes.JOB_NOT_FOUND, "zh");

        assertThat(service.resolve(ErrorCodes.JOB_NOT_FOUND, "zh")).isEqualTo("任务不存在。");
        assertThat(service.resolve(ErrorCodes.JOB_NOT_FOUND, "en")).isEqualTo("The task does not exist.");
    }

    @Test
    void unknownKeysStayStable() {
        assertThat(service.resolve("NOT_REGISTERED", "zh")).isEqualTo("NOT_REGISTERED");
        assertThat(service.resolve("NOT_REGISTERED", "en")).isEqualTo("NOT_REGISTERED");
    }

    @Test
    void aBlankKeyFallsBackToTheGenericCode() {
        assertThat(service.resolve("", "zh")).isEqualTo("生成服务暂时不可用，请稍后重试。");
        assertThat(service.resolve(null, "en"))
                .isEqualTo("The generation service is temporarily unavailable. Please try again later.");
    }

    @Test
    void placeholdersAreInterpolated() {
        assertThat(service.resolve(I18nDefaultCatalog.KEY_JOB_FAILED_RETRY, "zh",
                Map.of("attempt", 2, "max_attempts", 3)))
                .isEqualTo("生成服务异常，正在重试（第 2/3 次）。");
        assertThat(service.resolve(I18nDefaultCatalog.KEY_JOB_FAILED_RETRY, "en",
                Map.of("attempt", 2, "max_attempts", 3)))
                .isEqualTo("Generation issue, retrying (2/3).");
    }

    @Test
    void missingParametersKeepThePlaceholder() {
        assertThat(service.resolve(I18nDefaultCatalog.KEY_JOB_FAILED_RETRY, "zh", Map.of("attempt", 2)))
                .isEqualTo("生成服务异常，正在重试（第 2/{max_attempts} 次）。");
        assertThat(service.resolve(I18nDefaultCatalog.KEY_JOB_FAILED_RETRY, "zh"))
                .isEqualTo("生成服务异常，正在重试（第 {attempt}/{max_attempts} 次）。");
    }

    @Test
    void parameterValuesAreSanitized() {
        String text = service.resolve(I18nDefaultCatalog.KEY_JOB_FAILED_RETRY, "zh",
                Map.of("attempt", "2\r\n注入", "max_attempts", "x".repeat(200)));

        assertThat(text).doesNotContain("\r").doesNotContain("\n");
        assertThat(text).isEqualTo("生成服务异常，正在重试（第 2注入/" + "x".repeat(64) + " 次）。");
    }

    @Test
    void databaseFailuresNeverBreakTheCaller() {
        I18nMessageService broken = new I18nMessageService(new BrokenRepository());

        assertThat(broken.resolve(ErrorCodes.JOB_FAILED, "zh")).isEqualTo("任务执行失败。");
    }

    @Test
    void localeParsing() {
        assertThat(service.localeOf(null)).isEqualTo("zh");
        assertThat(service.localeOf("")).isEqualTo("zh");
        assertThat(service.localeOf("en-US,en;q=0.9")).isEqualTo("en");
        assertThat(service.localeOf("zh-CN,zh;q=0.9")).isEqualTo("zh");
        assertThat(service.localeOf(" EN-gb ")).isEqualTo("en");
    }

    /** 模拟 DB 挂掉：查词条炸了也不许炸穿主流程。 */
    private static final class BrokenRepository implements I18nMessageRepository {

        @Override
        public Optional<I18nMessage> findByKeyAndLocale(String key, String locale) {
            throw new IllegalStateException("relation \"i18n_message\" does not exist");
        }

        @Override
        public List<I18nMessage> findByKey(String key) {
            throw new IllegalStateException("relation \"i18n_message\" does not exist");
        }

        @Override
        public I18nMessage upsert(String key, String locale, String template) {
            throw new IllegalStateException("relation \"i18n_message\" does not exist");
        }

        @Override
        public List<I18nMessage> search(String query) {
            throw new IllegalStateException("relation \"i18n_message\" does not exist");
        }
    }
}
