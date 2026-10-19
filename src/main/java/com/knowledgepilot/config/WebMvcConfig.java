package com.knowledgepilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.knowledgepilot.auth.AuthInterceptor;

/**
 * Spring MVC 配置：把鉴权拦截器接到请求链上。
 *
 * <p>
 * 为什么实现 {@link WebMvcConfigurer} 而不是继承 {@code WebMvcConfigurationSupport}：
 * 前者是“增量式扩展”，Spring Boot 的 MVC 自动配置（消息转换器、静态资源、OpenAPI 等）全部保留；
 * 后者会接管全部 MVC 配置，导致自动配置整体失效，需要手工重建一堆默认配置。
 * </p>
 *
 * <p>
 * 拦截范围只限 {@code /api/**}，避免误拦 swagger-ui、{@code /v3/api-docs}、{@code /error}
 * 以及静态资源——否则这些不需要登录的请求会被鉴权器拦成 401。
 * </p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 鉴权拦截器，由 Spring 注入（其自身已声明为 @Component） */
    private final AuthInterceptor authInterceptor;

    /** 构造器注入：配置类同样遵循依赖不可变原则，单构造器无需 @Autowired */
    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * 注册拦截器及其生效范围。
     *
     * <p>
     * 白名单（excludePathPatterns）都是“不能要求登录”的入口：
     * <ul>
     *   <li>{@code /api/users}：用户注册，未登录用户必须能访问，否则无法获得账号；</li>
     *   <li>{@code /api/auth/login}：登录接口，本身就是为了换取 token；若被拦截会死循环；</li>
     *   <li>{@code /api/auth/logout}：登出，必须允许用无效/过期 token 调用且幂等，
     *       否则会话过期后用户连“登出”都做不到。</li>
     * </ul>
     * </p>
     *
     * <p>
     * 排除项按路径精确匹配（不是前缀匹配）：{@code /api/users} 只排除注册接口本身，
     * 将来新增的 {@code /api/users/{id}} 仍会被拦截，符合预期。
     * </p>
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            // 只拦 API 层：/api/** 以外的请求（swagger、错误页、静态资源）不进鉴权链
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/users",
                "/api/auth/login",
                "/api/auth/logout"
            );
    }
}
