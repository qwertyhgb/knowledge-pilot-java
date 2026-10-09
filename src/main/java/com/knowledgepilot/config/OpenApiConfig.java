package com.knowledgepilot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI knowledgePilotOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("KnowledgePilot API")
                .description("知识库服务接口，Spring Boot 4.1.1 + MyBatis-Plus 3.5.17 + MySQL 8.4")
                .version("0.0.1-SNAPSHOT")
                .contact(new Contact().name("KnowledgePilot")));
    }
}
