package com.system.service;

import com.system.dto_vo.vo.WxSessionResponse;

/**
 * 微信服务：与微信小程序服务端交互。
 */
public interface WeChatService {

    /** 用小程序登录 code 换取 openid/session_key */
    WxSessionResponse code2Session(String code);
}
