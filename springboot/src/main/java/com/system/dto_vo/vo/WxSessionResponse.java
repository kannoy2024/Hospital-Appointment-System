package com.system.dto_vo.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class WxSessionResponse {

    @JsonProperty("errcode")
    private Integer errcode;

    @JsonProperty("errmsg")
    private String errmsg;

    private String openid;

    @JsonProperty("session_key")
    private String sessionKey;

    private String unionid;

    public boolean isSuccess() {
        return openid != null && !openid.isBlank();
    }

}
