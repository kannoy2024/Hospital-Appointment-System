package com.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Data
@Component
@ConfigurationProperties(prefix = "wechat.miniapp")
public class WeChatMiniAppProperties {

    private String appId = "";
    private String appSecret = "";

    public boolean isConfigured() {
        return appId != null && !appId.isBlank()
                && appSecret != null && !appSecret.isBlank();
    }
}
