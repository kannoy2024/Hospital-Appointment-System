package com.system.controller;

import com.system.common.Log;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.AvatarBindRequest;
import com.system.dto_vo.dto.ChangePasswordRequest;
import com.system.dto_vo.dto.ProfileUpdateRequest;
import com.system.dto_vo.vo.UserVO;
import com.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "个人中心", description = "当前登录用户修改资料、头像、密码")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/profile")
@AllArgsConstructor
public class ProfileController {

    private final UserService userService;

    @Log(module = "个人中心", action = "修改资料")
    @Operation(summary = "修改个人资料", description = "可修改昵称、邮箱、手机号。")
    @PutMapping
    public Result<UserVO> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return Result.ok(userService.updateProfile(request));
    }

    @Log(module = "个人中心", action = "绑定头像")
    @Operation(summary = "绑定头像", description = "将已上传的头像路径写入用户表。须先调用「文件管理 → 上传文件」且 category=avatar。")
    @PutMapping("/avatar")
    public Result<UserVO> bindAvatar(@Valid @RequestBody AvatarBindRequest request) {
        return Result.ok(userService.bindAvatar(request.getPath()));
    }

    @Log(module = "个人中心", action = "修改密码")
    @Operation(summary = "修改密码", description = "校验原密码后修改；成功后需使用新密码重新登录。")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return Result.ok();
    }
}
