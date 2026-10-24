package com.flora.api.auth.infra;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 鉴权闸门。
 *
 * <p>拦截 {@code /api/v1/**}；放行名单只有 {@link AuthPathPolicy#REGISTER_PATH}
 * 与 {@link AuthPathPolicy#LOGIN_PATH}。{@code /api/v1/jobs/**}（WP4 落地）
 * 自动落在同一闸门下。
 *
 * <p>没有会话时 {@link StpUtil#checkLogin()} 抛 {@code NotLoginException}，
 * 由 {@code AuthExceptionHandler} 转成统一信封。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            String path = SaHolder.getRequest().getRequestPath();
            if (AuthPathPolicy.requiresLogin(path)) {
                StpUtil.checkLogin();
            }
        })).addPathPatterns(AuthPathPolicy.PROTECTED_PREFIX + "**");
    }
}
