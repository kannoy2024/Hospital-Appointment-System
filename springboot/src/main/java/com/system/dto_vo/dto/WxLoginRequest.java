package com.system.dto_vo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WxLoginRequest {

    @NotBlank(message = "code 不能为空")
    private String code;

    private String nickname;

    private String avatarUrl;


}
