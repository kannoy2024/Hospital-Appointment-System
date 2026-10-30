package com.system.controller;

import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.LoginRequest;
import com.system.dto_vo.dto.RegisterRequest;
import com.system.dto_vo.dto.WxLoginRequest;
import com.system.dto_vo.vo.LoginResponse;
import com.system.dto_vo.vo.UserVO;
import com.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth:认证", description = "登录、注册、获取当前用户（登录/注册无需 Token）")
@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;


    @Operation(summary = "用户登录", description = "校验用户名密码，返回 JWT 与用户信息。管理员跳转后台，用户跳转前台。")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }

    @Operation(summary = "用户注册", description = "注册新用户，默认角色为 USER（普通用户）")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(userService.register(request));
    }

    @Operation(summary = "微信小程序登录", description = "使用 wx.login 获取的 code 换取 JWT，首次登录自动注册患者账号。")
    @PostMapping("/wx-login")
    public Result<LoginResponse> wxLogin(@Valid @RequestBody WxLoginRequest request) {
//        log.info("打印测试，微信用户登录code授权码:{}",request.getCode());
        return Result.ok(userService.wxLogin(request));
    }

    @Operation(summary = "当前登录用户", description = "根据请求头中的 JWT 返回当前用户资料，需先 Authorize 填写 Token。")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.getCurrentUser());
    }
}
