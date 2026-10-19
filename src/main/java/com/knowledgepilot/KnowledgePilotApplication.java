package com.knowledgepilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * KnowledgePilot 启动类。
 *
 * <p>
 * {@code @SpringBootApplication} 开启自动配置与组件扫描（根包 com.knowledgepilot）；
 * {@code @MapperScan} 指定 Mapper 接口所在包，避免在每个 Mapper 上重复标 {@code @Mapper}。
 * 扫描范围只覆盖 mapper 包，不扩大到整个项目，防止误把普通接口注册成 MyBatis 代理。
 * </p>
 */
@SpringBootApplication
@MapperScan("com.knowledgepilot.mapper")
public class KnowledgePilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgePilotApplication.class, args);
    }
}
