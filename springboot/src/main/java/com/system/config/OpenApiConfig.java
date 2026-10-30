package com.system.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
* swagger生成接口文档的说明头部分
*
* http://localhost:9090/swagger-ui/index.html#/
* 可以访问到swagger-UI的说明文档
* */
@Configuration
public class OpenApiConfig {
    //载荷认证
    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(" 脚手架 API")
                        .description("""
                                Spring Boot + Vue 前后端分离脚手架接口文档。

                                **认证说明**
                                1. 先调用「认证 → 用户登录」获取 token
                                2. 点击右上角 **Authorize**，填入：`Bearer 你的token`（注意 Bearer 后有空格）
                                3. 登录、注册接口无需 Token

                                **默认账号**
                                - 管理员：admin / admin
                                - 用户：aaa / 123，bbb / 123
                                """)
                        .version("1.0")
                        .contact(new Contact().name("脚手架")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT 令牌，格式：Bearer {token}")));
    }
}
