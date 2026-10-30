package com.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/*
 * RestClient配置设置，
 * 提供微信登录，ai调用，微信支付，支付宝支付的基本路径管理*/


@Configuration
public class RestClientConfig {

    /**
     * AI 服务地址，从 application.yml 的 ai.api-url 读取
     */
    @Value("${ai.api-url:https://api.deepseek.com}")
    private String aiBaseUrl;

    //微信登录服务地址
    @Value("${wechat.miniapp.api-url:https://api.weixin.qq.com}")
    private String wxBaseUrl;

    //  ai导诊 API 
    @Bean
    public RestClient aiRestClient() {
        return RestClient.builder()
                .baseUrl(aiBaseUrl)                              // base URL，调用时只写路径
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)  // 公共头
                .requestFactory(requestFactory(5000, 30000))                // 配置超时
                .build();
    }

    //  微信小程序 API 
    @Bean
    public RestClient wechatRestClient() {
        return RestClient.builder()
                .baseUrl(wxBaseUrl)      // 微信接口根地址
                .requestFactory(requestFactory(5000, 10000))
                .build();
    }


    /**
     * 通用请求工厂：连接超时 + 读取超时
     */
    private SimpleClientHttpRequestFactory requestFactory(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}