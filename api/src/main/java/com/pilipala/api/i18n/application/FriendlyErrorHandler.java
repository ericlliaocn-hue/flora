package com.pilipala.api.i18n.application;

import java.util.Map;

import com.pilipala.api.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;

/**
 * 异常处理器的公共底座：把 error_code 翻成当前语言的友好短句。
 *
 * <p>C 端文案只有这一个出口，ADR 0005 的「不回显异常原文」在这里兜住。
 *
 * <p>窄切片（{@code @WebMvcTest}）里没有 i18n Bean 时回退到内置种子，
 * 现有切片测试无需为文案改写。
 */
public abstract class FriendlyErrorHandler {

    private final ObjectProvider<I18nMessageService> i18nProvider;
    private final ObjectProvider<RequestLocaleResolver> localeResolvers;

    protected FriendlyErrorHandler(ObjectProvider<I18nMessageService> i18nProvider,
            ObjectProvider<RequestLocaleResolver> localeResolvers) {
        this.i18nProvider = i18nProvider;
        this.localeResolvers = localeResolvers;
    }

    /** 当前请求该用哪种语言：登录用户偏好优先，其次 Accept-Language。 */
    protected String locale(HttpServletRequest request) {
        RequestLocaleResolver resolver = localeResolvers.getIfAvailable();
        if (resolver != null) {
            return resolver.resolve(request);
        }
        return I18nDefaultCatalog.localeOf(request == null ? null : request.getHeader("Accept-Language"));
    }

    /** error_code → 友好短句；绝不回显异常原文。 */
    protected String message(String errorCode, String locale) {
        I18nMessageService i18n = i18nProvider.getIfAvailable();
        if (i18n != null) {
            return i18n.resolve(errorCode, locale);
        }
        return I18nDefaultCatalog.resolveDefault(errorCode, locale, Map.of());
    }

    /** 统一信封：HTTP 200 + ok=false + error_code + message。 */
    protected ApiResponse<Void> failure(String errorCode, HttpServletRequest request) {
        return ApiResponse.error(errorCode, message(errorCode, locale(request)));
    }
}
