package com.system.controller.admin;


import com.system.common.Log;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.service.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@Tag(name = "系统状态管理", description = "管理员控制系统状态")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
public class SystemAdminController {

    private final SystemService systemService;


    /**
     * 管理员设置系统当前状态
     * 存储在Redis中
     *
     * @param status 系统状态
     * @return void
     */
    @Log(module = "系统管理", action = "设置系统状态")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "设置系统状态")
    @PutMapping("/{status}")
    public Result<Void> setStatus(@PathVariable Integer status) {
        systemService.setStatus(status);
        return Result.ok();
    }


    @Log(module = "系统管理", action = "获取系统状态")
    @Operation(summary = "获取系统状态")
    @GetMapping("/status")
    public Result<String> getStatus() {
        return Result.ok(systemService.getStatus());
    }

}
