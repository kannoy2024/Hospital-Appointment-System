package com.system.dto_vo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserSaveRequest {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度为3-50个字符")
    private String username;

    @Size(min = 3, max = 50, message = "密码长度为3-50个字符")
    private String password;

    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    @Schema(description = "性别")
    private Integer gender;

    @NotNull(message = "状态不能为空")
    private Integer status;

    @NotBlank(message = "角色不能为空")
    private String role;


}
