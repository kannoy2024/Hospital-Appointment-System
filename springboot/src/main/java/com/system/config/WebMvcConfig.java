package com.system.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 * 1. CORS跨域配置
 * 2. 静态资源映射（上传文件访问）
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {


    /**
     * CORS跨域配置
     * 允许前端（Vue/微信小程序）跨域访问后端API
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")                    // 所有接口
                .allowedOriginPatterns("*")           // 允许所有来源
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // 允许的方法
                .allowedHeaders("*")                  // 允许所有请求头
                .allowCredentials(true)               // 允许携带凭证（Cookie/Token）
                .maxAge(3600);                        // 预检请求缓存时间（秒）
    }

}