package com.system.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.system.common.BusinessException;
import com.system.config.WeChatMiniAppProperties;
import com.system.dto_vo.vo.WxSessionResponse;
import com.system.service.WeChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 微信小程序服务实现：负责用登录 code 换取 openid，供患者端登录绑定。
 */
@Service
@RequiredArgsConstructor
public class WeChatServiceImpl implements WeChatService {

    private final WeChatMiniAppProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient wechatRestClient;


    /**
     * 用 code 换取微信 openid，供小程序登录绑定。
     *
     * @param code 微信登录临时凭证
     * @return 微信会话信息
     */
    @Override
    public WxSessionResponse code2Session(String code) {
        if (!properties.isConfigured()) {
            throw new BusinessException("微信小程序 AppSecret 未配置，请在 application-local.yml 设置 wechat.miniapp.app-secret");
        }

        String body = wechatRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sns/jscode2session")                                  // 路径
                        .queryParam("appid", properties.getAppId())             //传微信小程序的AppID
                        .queryParam("secret", properties.getAppSecret())        //传小程序secret
                        .queryParam("js_code", code)                            //5分钟后失效的code
                        .queryParam("grant_type", "authorization_code") //授权类型,固定填写的内容
                        .build())
                .retrieve()                             //发送请求，并接收响应
                .body(String.class);                    //把响应体转成 String

        WxSessionResponse response;
        try {
            response = objectMapper.readValue(body, WxSessionResponse.class);
        } catch (Exception e) {
            throw new BusinessException("微信登录失败：响应解析错误");
        }

        if (response == null || !response.isSuccess()) {
            String msg = response != null && response.getErrmsg() != null
                    ? response.getErrmsg()
                    : "微信登录失败";
            throw new BusinessException("微信登录失败：" + msg);
        }
        return response;
    }
}
