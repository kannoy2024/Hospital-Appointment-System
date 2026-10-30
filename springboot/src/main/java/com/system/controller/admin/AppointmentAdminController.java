package com.system.controller.admin;

import com.system.common.Log;
import com.system.common.PageResult;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.vo.AppointmentVO;
import com.system.service.AppointmentExportService;
import com.system.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "预约管理", description = "管理员预约查询与取消")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/admin/appointments")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class AppointmentAdminController {

    private final AppointmentService appointmentService;
    private final AppointmentExportService appointmentExportService;

    //分页查询预约
    @Operation(summary = "全部预约分页")
    @GetMapping
    public Result<PageResult<AppointmentVO>> page(
            @Parameter(description = "当前页码", example = "1")
            @RequestParam(defaultValue = "1") long current,

            @Parameter(description = "每页条数", example = "10")
            @RequestParam(defaultValue = "10") long size,

            @Parameter(description = "预约状态 筛选")
            @RequestParam(required = false) String status,

            @Parameter(description = "预约日期筛选")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDate,

            @Parameter(description = "关键词筛选")
            @RequestParam(required = false) String keyword) {
        return Result.ok(appointmentService.pageAdmin(current, size, status, scheduleDate, keyword));
    }

    //管理员预约取消
    @Log(module = "预约管理", action = "管理员取消预约")
    @Operation(summary = "管理员取消预约")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        appointmentService.cancelByAdmin(id);
        return Result.ok();
    }

    //管理员预约管理
    @Log(module = "预约管理", action = "标记就诊完成")
    @Operation(summary = "管理员标记就诊完成")
    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        appointmentService.completeByAdmin(id);
        return Result.ok();
    }

    //管理员导出预约明细表
    @Log(module = "预约管理", action = "导出预约明细")
    @Operation(summary = "导出预约明细(Excel)")
    @GetMapping("/export")
    public void export(
            HttpServletResponse response,

            @Parameter(description = "预约状态 筛选")
            @RequestParam(required = false) String status,

            @Parameter(description = "预约日期筛选")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDate,

            @Parameter(description = "关键词筛选")
            @RequestParam(required = false) String keyword) {
        appointmentExportService.export(response, status, scheduleDate, keyword);

    }

}
