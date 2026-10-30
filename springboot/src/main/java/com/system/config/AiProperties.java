package com.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private boolean enabled = false;
    private String apiUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-v4-flash";

}
