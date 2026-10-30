package com.system.controller;

import com.system.common.Result;
import com.system.dto_vo.vo.DepartmentVO;
import com.system.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "科室", description = "患者端科室查询")
@RestController
@RequestMapping("/api/departments")
@AllArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @Operation(summary = "启用科室列表")
    @GetMapping
    public Result<List<DepartmentVO>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(departmentService.listActive(keyword));
    }

    @Operation(summary = "科室详情")
    @GetMapping("/{id}")
    public Result<DepartmentVO> getById(@PathVariable Long id) {
        return Result.ok(departmentService.getById(id));
    }
}
