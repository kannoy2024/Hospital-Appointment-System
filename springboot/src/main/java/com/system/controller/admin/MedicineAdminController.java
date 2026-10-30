package com.system.controller.admin;

import com.system.common.Log;
import com.system.common.PageResult;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.MedicineSaveRequest;
import com.system.dto_vo.vo.MedicineVO;
import com.system.service.MedicineExportService;
import com.system.service.MedicineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "药品管理", description = "管理员药品库 CRUD")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/admin/medicines")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class MedicineAdminController {

    private final MedicineService medicineService;
    private final MedicineExportService medicineExportService;

    @Operation(summary = "药品分页列表")
    @GetMapping
    public Result<PageResult<MedicineVO>> page(
            @Parameter(description = "当前页码，从 1 开始")
            @RequestParam(defaultValue = "1") long current,

            @Parameter(description = "每页条数")
            @RequestParam(defaultValue = "10") long size,

            @Parameter(description = "搜索关键字，可选")
            @RequestParam(required = false) String keyword) {
        return Result.ok(medicineService.pageAdmin(current, size, keyword));
    }

    @Operation(summary = "药品详情")
    @GetMapping("/{id}")
    public Result<MedicineVO> getById(@PathVariable Long id) {
        return Result.ok(medicineService.getById(id));
    }

    @Log(module = "药品管理", action = "新增药品")
    @Operation(summary = "新增药品")
    @PostMapping
    public Result<MedicineVO> create(@Valid @RequestBody MedicineSaveRequest request) {
        return Result.ok(medicineService.create(request));
    }

    @Log(module = "药品管理", action = "修改药品")
    @Operation(summary = "修改药品")
    @PutMapping("/{id}")
    public Result<MedicineVO> update(@PathVariable Long id, @Valid @RequestBody MedicineSaveRequest request) {
        request.setId(id);
        return Result.ok(medicineService.update(request));
    }

    @Log(module = "药品管理", action = "删除药品")
    @Operation(summary = "删除药品")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        medicineService.delete(id);
        return Result.ok();
    }


    //管理员导出药品明细表
    @Log(module = "药品管理", action = "导出药品明细")
    @Operation(summary = "导出药品明细(Excel)")
    @GetMapping("/export")
    public void export(
            HttpServletResponse response,

            @Parameter(description = "关键词筛选")
            @RequestParam(required = false) String keyword) {
        medicineExportService.export(response, keyword);

    }
}
