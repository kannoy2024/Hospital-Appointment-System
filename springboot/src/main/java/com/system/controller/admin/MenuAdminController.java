package com.system.controller.admin;

import com.system.common.Log;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.vo.MenuAdminVO;
import com.system.dto_vo.dto.MenuRoleAssignRequest;
import com.system.dto_vo.dto.MenuSaveRequest;
import com.system.dto_vo.vo.MenuVO;
import com.system.service.MenuService;
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

@Tag(name = "菜单", description = "当前用户菜单与管理员菜单配置")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/admin/menus")
@AllArgsConstructor
public class MenuAdminController {

    private final MenuService menuService;


    @Operation(summary = "当前用户后台菜单",
            description = "按当前登录用户的角色从 sys_role_menu 返回可见的 /admin/* 菜单；USER 返回空（前台菜单写死在前端）。")
    @GetMapping
    public Result<List<MenuVO>> myMenus() {
        return Result.ok(menuService.listMyMenus());
    }

    @Operation(summary = "菜单列表（管理）", description = "管理员查看全部菜单及其可见角色，用于菜单权限配置。")
    @GetMapping("/manage")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<MenuAdminVO>> manageList(
            @Parameter(description = "搜索关键字，匹配名称/路径/图标")
            @RequestParam(required = false) String keyword) {
        return Result.ok(menuService.listAllForAdmin(keyword));
    }

    @Log(module = "菜单管理", action = "新增菜单")
    @Operation(summary = "新增菜单")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MenuAdminVO> create(@Valid @RequestBody MenuSaveRequest request) {
        return Result.ok(menuService.createMenu(request));
    }

    @Log(module = "菜单管理", action = "修改菜单")
    @Operation(summary = "修改菜单")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MenuAdminVO> update(
            @Parameter(description = "菜单 ID") @PathVariable Long id,
            @Valid @RequestBody MenuSaveRequest request) {
        request.setId(id);
        return Result.ok(menuService.updateMenu(request));
    }

    @Log(module = "菜单管理", action = "分配角色")
    @Operation(summary = "修改菜单可见角色",
            description = "全量替换该菜单的角色关联。"
                    + "注意：菜单可见性只影响侧栏显示，接口真正的访问边界在各 Controller 的 @PreAuthorize 上。")
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MenuAdminVO> assignRoles(
            @Parameter(description = "菜单 ID") @PathVariable Long id,
            @Valid @RequestBody MenuRoleAssignRequest request) {
        return Result.ok(menuService.assignRoles(id, request.getRoleCodes()));
    }

    @Log(module = "菜单管理", action = "批量删除菜单")
    @Operation(summary = "批量删除菜单", description = "内置菜单不可删；存在子菜单时需先删子菜单。")
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteBatch(@RequestBody List<Long> ids) {
        menuService.deleteMenus(ids);
        return Result.ok();
    }

    @Log(module = "菜单管理", action = "删除菜单")
    @Operation(summary = "删除菜单")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(
            @Parameter(description = "菜单 ID")
            @PathVariable Long id) {
        menuService.deleteMenu(id);
        return Result.ok();
    }
}
