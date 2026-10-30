package com.system.controller.admin;

import com.system.common.Log;
import com.system.common.PageResult;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.LogBatchDeleteRequest;
import com.system.dto_vo.vo.SystemLogVO;
import com.system.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统日志", description = "登录日志与操作日志查询（需 ADMIN 角色）")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/admin/logs")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class LogAdminController {

    private final LogService logService;


    @Operation(summary = "系统日志分页", description = "type=login 为登录日志，type=operation 为操作日志（带 @Log 注解的接口）。")
    @GetMapping
    public Result<PageResult<SystemLogVO>> systemLogs(
            @Parameter(description = "当前页码")
            @RequestParam(defaultValue = "1") long current,

            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") long size,

            @Parameter(description = "按用户名筛选，可选")
            @RequestParam(required = false) String username,

            @Parameter(description = "日志类型：login | operation", example = "login")
            @RequestParam(defaultValue = "login") String type) {
        return Result.ok(logService.pageSystemLogs(current, size, username, type));
    }

    @Log(module = "系统日志", action = "批量删除日志")
    @Operation(summary = "批量删除日志", description = "type=login 删除登录日志；type=operation 删除操作日志。")
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@Valid @RequestBody LogBatchDeleteRequest request) {
        logService.deleteLogs(request.getType(), request.getIds());
        return Result.ok();
    }
}
