package com.xiaozhi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

@Configuration
public class SwaggerConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("小智物联网平台 API")
                        .description("小智ESP32智能物联网管理平台的RESTful API文档")
                        .version("1.0.0")
                        .termsOfService("https://example.com/terms/")
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html"))
                        .contact(new Contact()
                                .name("Joey")
                                .email("joey@example.com")));
    }
}
