package com.system.dto_vo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @Schema(description = "用户名", example = "testUser")
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度为3-50个字符")
    private String username;

    @Schema(description = "密码", example = "123456")
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 50, message = "密码长度为6-50个字符")
    private String password;

    @Schema(description = "昵称，可选")
    private String nickname;

    @Schema(description = "邮箱，可选")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "手机号，可选")
    private String phone;

    @Schema(description = "性别")
    private Integer gender;


}
