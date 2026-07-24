package com.example.onlineexamsystem.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI onlineExamOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("在线考试系统 API")
                        .description("在线考试系统 + 智能学习伙伴 — REST API 文档")
                        .version("1.0.0")
                        .contact(new Contact().name("开发团队").email("admin@example.com"))
                        .license(new License().name("MIT")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .schemaRequirement("Bearer", new SecurityScheme()
                        .name("Bearer")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("在登录接口获取 token，格式：Bearer <token>"));
    }
}
