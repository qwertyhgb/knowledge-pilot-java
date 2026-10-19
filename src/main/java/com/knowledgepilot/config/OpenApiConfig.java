package com.knowledgepilot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置。
 *
 * <p>
 * 只定义文档元信息（标题、描述、版本、联系人）；接口分组与说明由各 Controller 的
 * {@code @Tag} / {@code @Operation} 提供。Swagger UI 地址由 application.yml 的
 * springdoc 配置决定（默认 /swagger-ui.html）。
 * </p>
 *
 * <p>
 * {@code proxyBeanMethods = false}：轻量配置模式，不生成 CGLIB 代理；
 * 本类没有 Bean 间方法调用，无需保留代理。
 * </p>
 *
 * <p>
 * 注意：这里的 version 是手写值，升级 pom 版本时需要同步，否则文档与实际构建不一致。
 * </p>
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    /**
     * 构建全局 OpenAPI 文档。
     *
     * @return OpenAPI 元信息
     */
    @Bean
    public OpenAPI knowledgePilotOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("KnowledgePilot API")
                .description("知识库服务接口，Spring Boot 4.1.1 + MyBatis-Plus 3.5.17 + MySQL 8.4")
                .version("0.0.1-SNAPSHOT")
                .contact(new Contact().name("KnowledgePilot")));
    }
}
