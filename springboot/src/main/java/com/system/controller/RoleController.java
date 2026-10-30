package com.system.controller;

import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.vo.RoleVO;
import com.system.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "角色", description = "系统角色列表，供用户管理等下拉选择")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/roles")
@AllArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "启用角色列表", description = "返回 status=启用 的角色，默认含 ADMIN、USER。")
    @GetMapping
    public Result<List<RoleVO>> list() {
        return Result.ok(roleService.listEnabledRoles());
    }
}
