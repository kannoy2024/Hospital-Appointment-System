package com.system.controller.admin;

import com.system.common.Log;
import com.system.common.PageResult;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.ScheduleSaveRequest;
import com.system.dto_vo.vo.ScheduleVO;
import com.system.service.ScheduleExportService;
import com.system.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "排班管理", description = "管理员排班 CRUD")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/admin/schedules")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class ScheduleAdminController {

    private final ScheduleService scheduleService;
    private final ScheduleExportService scheduleExportService;

    @Log(module = "排班管理", action = "查看排班信息")
    @Operation(summary = "排班分页列表")
    @GetMapping
    public Result<PageResult<ScheduleVO>> page(
            @Parameter(description = "当前页码，从 1 开始")
            @RequestParam(defaultValue = "1") long current,

            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") long size,

            @Parameter(description = "医生id")
            @RequestParam(required = false) Long doctorId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDateStart,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDateEnd) {
        return Result.ok(scheduleService.pageAdmin(current, size, doctorId, scheduleDateStart, scheduleDateEnd));
    }

    @Operation(summary = "排班详情")
    @GetMapping("/{id}")
    public Result<ScheduleVO> getById(@PathVariable Long id) {
        return Result.ok(scheduleService.getById(id));
    }

    @Log(module = "排班管理", action = "新增排班")
    @Operation(summary = "新增排班")
    @PostMapping
    public Result<ScheduleVO> create(@Valid @RequestBody ScheduleSaveRequest request) {
        return Result.ok(scheduleService.create(request));
    }

    @Log(module = "排班管理", action = "修改排班")
    @Operation(summary = "修改排班")
    @PutMapping("/{id}")
    public Result<ScheduleVO> update(@PathVariable Long id, @Valid @RequestBody ScheduleSaveRequest request) {
        request.setId(id);
        return Result.ok(scheduleService.update(request));
    }

    @Log(module = "排班管理", action = "删除排班")
    @Operation(summary = "删除排班")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.ok();
    }

    //管理员导出排班明细表
    @Log(module = "排班管理", action = "导出排班明细")
    @Operation(summary = "导出排班明细(Excel)")
    @GetMapping("/export")
    public void export(
            HttpServletResponse response,

            @Parameter(description = "开始日期，不传则默认当月第一天")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDateStart,

            @Parameter(description = "结束日期，不传则默认当月最后一天")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDateEnd) {
        scheduleExportService.export(response,scheduleDateStart,scheduleDateEnd);

    }

}
