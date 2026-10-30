package com.system.controller.admin;

import com.system.common.Log;
import com.system.common.PageResult;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.UserSaveRequest;
import com.system.dto_vo.vo.UserVO;
import com.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理员用户管理", description = "管理员对用户增删改查（需 ADMIN 角色）")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class UserAdminController {

    private final UserService userService;

    @Log(module = "管理员端用户查询", action = "查询用户列表")
    @Operation(summary = "用户分页列表", description = "支持按用户名、昵称、邮箱关键字模糊搜索。")
    @GetMapping
    public Result<PageResult<UserVO>> page(
            @Parameter(description = "当前页码，从 1 开始")
            @RequestParam(defaultValue = "1") long current,

            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") long size,

            @Parameter(description = "搜索关键字，可选")
            @RequestParam(required = false) String keyword) {
        return Result.ok(userService.pageUsers(current, size, keyword));
    }

    @Operation(summary = "用户详情", description = "根据用户 ID 查询单条记录。")
    @GetMapping("/{id}")
    public Result<UserVO> getById(
            @Parameter(description = "用户 ID")
            @PathVariable Long id) {
        return Result.ok(userService.getUserById(id));
    }

    @Log(module = "用户管理", action = "新增用户")
    @Operation(summary = "新增用户", description = "创建用户并指定角色，密码必填。")
    @PostMapping
    public Result<UserVO> create(@Valid @RequestBody UserSaveRequest request) {
        return Result.ok(userService.createUser(request));
    }

    @Log(module = "用户管理", action = "修改用户")
    @Operation(summary = "修改用户", description = "更新用户信息；密码留空表示不修改密码。")
    @PutMapping("/{id}")
    public Result<UserVO> update(
            @Parameter(description = "用户 ID")
            @PathVariable Long id,

            @Valid @RequestBody UserSaveRequest request) {
        request.setId(id);
        return Result.ok(userService.updateUser(request));
    }

    @Log(module = "用户管理", action = "批量删除用户")
    @Operation(summary = "批量删除用户", description = "不能删除当前登录用户；至少保留一名管理员。")
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        userService.deleteUsers(ids);
        return Result.ok();
    }

    @Log(module = "用户管理", action = "删除用户")
    @Operation(summary = "删除用户", description = "逻辑删除，不能删除当前登录的管理员自己。")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "用户 ID")
            @PathVariable Long id) {
        userService.deleteUser(id);
        return Result.ok();
    }
}
