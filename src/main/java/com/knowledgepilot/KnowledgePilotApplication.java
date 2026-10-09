package com.knowledgepilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.knowledgepilot.mapper")
public class KnowledgePilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgePilotApplication.class, args);
    }
}
