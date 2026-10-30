package com.system.controller.admin;

import com.system.common.Log;
import com.system.common.PageResult;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.DoctorSaveRequest;
import com.system.dto_vo.vo.DoctorAdminVO;
import com.system.dto_vo.vo.DoctorVO;
import com.system.service.DoctorService;
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

@Tag(name = "医生管理", description = "管理员医生 CRUD")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/admin/doctors")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class DoctorAdminController {

    private final DoctorService doctorService;

    @Operation(summary = "医生分页列表")
    @GetMapping
    public Result<PageResult<DoctorAdminVO>> page(
            @Parameter(description = "当前页码，从 1 开始")
            @RequestParam(defaultValue = "1") long current,

            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") long size,

            @Parameter(description = "部门id")
            @RequestParam(required = false) Long departmentId,

            @Parameter(description = "关键词：可选")
            @RequestParam(required = false) String keyword) {
        return Result.ok(doctorService.pageAdmin(current, size, departmentId, keyword));
    }

    @Operation(summary = "医生详情")
    @GetMapping("/{id}")
    public Result<DoctorAdminVO> getById(@PathVariable Long id) {
        return Result.ok(doctorService.getAdminById(id));
    }

    @Log(module = "医生管理", action = "新增医生")
    @Operation(summary = "新增医生")
    @PostMapping
    public Result<DoctorAdminVO> create(@Valid @RequestBody DoctorSaveRequest request) {
        return Result.ok(doctorService.create(request));
    }

    @Log(module = "医生管理", action = "修改医生")
    @Operation(summary = "修改医生")
    @PutMapping("/{id}")
    public Result<DoctorAdminVO> update(@PathVariable Long id, @Valid @RequestBody DoctorSaveRequest request) {
        request.setId(id);
        return Result.ok(doctorService.update(request));
    }

    @Log(module = "医生管理", action = "删除医生")
    @Operation(summary = "删除医生")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        doctorService.delete(id);
        return Result.ok();
    }
}
