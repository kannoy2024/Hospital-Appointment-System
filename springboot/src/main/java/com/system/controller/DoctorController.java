package com.system.controller;

import com.system.common.Log;
import com.system.common.Result;
import com.system.dto_vo.vo.DoctorVO;
import com.system.service.DoctorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "医生", description = "患者端医生查询")
@RestController
@RequestMapping("/api/doctors")
@AllArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    @Operation(summary = "医生列表")
    @GetMapping
    public Result<List<DoctorVO>> list(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean hasAvailableSlot) {
        return Result.ok(doctorService.listActive(departmentId, keyword, hasAvailableSlot));
    }

    @Operation(summary = "医生详情")
    @GetMapping("/{id}")
    public Result<DoctorVO> getById(@PathVariable Long id) {
        return Result.ok(doctorService.getById(id));
    }
}
