package com.system.controller.admin;

import com.system.common.Log;
import com.system.common.PageResult;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.DepartmentSaveRequest;
import com.system.dto_vo.vo.DepartmentVO;
import com.system.service.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
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

@Tag(name = "科室管理", description = "管理员科室 CRUD")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/admin/departments")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class DepartmentAdminController {

    private final DepartmentService departmentService;

    //管理员科室分页
    @Operation(summary = "科室分页列表")
    @GetMapping
    public Result<PageResult<DepartmentVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(departmentService.pageAdmin(current, size, keyword));
    }

    //管理员查看科室详情
    @Operation(summary = "科室详情")
    @GetMapping("/{id}")
    public Result<DepartmentVO> getById(@PathVariable Long id) {
        return Result.ok(departmentService.getById(id));
    }

    //管理员新增科室
    @Log(module = "科室管理", action = "新增科室")
    @Operation(summary = "新增科室")
    @PostMapping
    public Result<DepartmentVO> create(@Valid @RequestBody DepartmentSaveRequest request) {
        return Result.ok(departmentService.create(request));
    }

    //管理员修改科室
    @Log(module = "科室管理", action = "修改科室")
    @Operation(summary = "修改科室")
    @PutMapping("/{id}")
    public Result<DepartmentVO> update(@PathVariable Long id, @Valid @RequestBody DepartmentSaveRequest request) {
        request.setId(id);
        return Result.ok(departmentService.update(request));
    }

    //管理员删除科室
    @Log(module = "科室管理", action = "删除科室")
    @Operation(summary = "删除科室")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return Result.ok();
    }
}
