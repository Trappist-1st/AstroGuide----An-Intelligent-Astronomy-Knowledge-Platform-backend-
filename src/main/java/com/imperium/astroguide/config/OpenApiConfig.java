package com.imperium.astroguide.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI astroGuideOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AstroGuide API")
                        .description("AstroGuide 天文知识智能平台后端接口文档")
                        .version("v0")
                        .contact(new Contact().name("AstroGuide Team")))
                .servers(List.of(
                        new Server().url("http://localhost:8093").description("Local")
                ));
    }
}
