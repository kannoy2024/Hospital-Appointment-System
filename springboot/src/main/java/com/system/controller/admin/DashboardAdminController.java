package com.system.controller.admin;

import com.system.common.Log;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.vo.DashboardStatsVO;
import com.system.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据概览", description = "管理端仪表盘统计")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class DashboardAdminController {

    private final DashboardService dashboardService;

    @Log(module = "仪表盘管理", action = "仪表盘查询")
    @Operation(summary = "仪表盘统计")
    @GetMapping("/stats")
    public Result<DashboardStatsVO> stats() {
        return Result.ok(dashboardService.getStats());
    }
}
