package com.flora.api.job.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.flora.api.common.ErrorCodes;
import org.junit.jupiter.api.Test;

/**
 * Job 域错误短句：zh / en 都有，且不回退到上游原文。
 */
class JobErrorMessagesTest {

    @Test
    void providerUnavailableHasChineseAndEnglishText() {
        assertThat(JobErrorMessages.resolve(ErrorCodes.PROVIDER_UNAVAILABLE, "zh"))
                .isEqualTo("生成服务暂时不可用，请稍后重试。");
        assertThat(JobErrorMessages.resolve(ErrorCodes.PROVIDER_UNAVAILABLE, "en"))
                .isEqualTo("The generation service is temporarily unavailable. Please try again later.");
    }

    @Test
    void everyEnabledCodeHasText() {
        for (String code : new String[]{
                ErrorCodes.UNAUTHORIZED,
                ErrorCodes.VALIDATION_ERROR,
                ErrorCodes.JOB_NOT_FOUND,
                ErrorCodes.JOB_CANCELLED,
                ErrorCodes.IDEMPOTENCY_CONFLICT,
                ErrorCodes.JOB_FAILED,
                ErrorCodes.PROVIDER_UNAVAILABLE}) {
            assertThat(JobErrorMessages.resolve(code, "zh")).isNotEqualTo(code);
            assertThat(JobErrorMessages.resolve(code, "en")).isNotEqualTo(code);
        }
    }

    @Test
    void localeFallsBackToChineseAndUnknownCodesStayStable() {
        assertThat(JobErrorMessages.localeOf(null)).isEqualTo("zh");
        assertThat(JobErrorMessages.localeOf("")).isEqualTo("zh");
        assertThat(JobErrorMessages.localeOf("en-US,en;q=0.9")).isEqualTo("en");
        assertThat(JobErrorMessages.localeOf("zh-CN,zh;q=0.9")).isEqualTo("zh");
        assertThat(JobErrorMessages.resolve("NOT_REGISTERED", "zh")).isEqualTo("NOT_REGISTERED");
    }
}
