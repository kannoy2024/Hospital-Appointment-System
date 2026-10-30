package com.system.config;

import org.springframework.context.annotation.Configuration;

/**
 * 支付模块装配入口。
 * 配置属性绑定由 WeChatPayProperties 自身的 @Component 完成（与 AiProperties / WeChatMiniAppProperties 同一风格），
 * 这里保持空的 @Configuration，作为后续 Payment 相关 Bean 的统一装配点。
 */
@Configuration
public class PaymentConfig {
}
