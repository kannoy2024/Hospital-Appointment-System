package com.system.controller;

import com.system.common.Log;
import com.system.common.Result;
import com.system.dto_vo.vo.ScheduleVO;
import com.system.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "排班", description = "患者端可预约排班查询")
@RestController
@RequestMapping("/api/schedules")
@AllArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "可预约排班列表")
    @GetMapping
    public Result<List<ScheduleVO>> list(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDate) {
        return Result.ok(scheduleService.listAvailable(departmentId, doctorId, scheduleDate));
    }

    @Operation(summary = "排班详情")
    @GetMapping("/{id}")
    public Result<ScheduleVO> getById(@PathVariable Long id) {
        return Result.ok(scheduleService.getById(id));
    }
}
