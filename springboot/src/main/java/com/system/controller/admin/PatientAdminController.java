package com.system.controller.admin;

import com.system.common.Log;
import com.system.common.PageResult;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.vo.PatientVO;
import com.system.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "管理员就诊人管理", description = "管理员只读查询就诊人信息")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/admin/patients")
@RequiredArgsConstructor
public class PatientAdminController {

    private final PatientService patientService;

    //管理员接口
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "就诊人管理", action = "管理员查询就诊人")
    @Operation(summary = "管理员获取就诊人分页列表")
    @GetMapping
    public Result<PageResult<PatientVO>> pageAdmin(

            @RequestParam(defaultValue = "1")
            long current,

            @RequestParam(defaultValue = "10")
            long size,

            @RequestParam(required = false)
            String keyword) {
        return Result.ok(patientService.pageAdmin(current, size, keyword));
    }


    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "就诊人详情（管理员）")
    @GetMapping("/{id}")
    public Result<PatientVO> getById(@PathVariable Long id) {
        return Result.ok(patientService.getById(id));
    }
}
