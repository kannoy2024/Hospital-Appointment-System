package com.system.controller;

import com.system.common.Log;
import com.system.common.PageResult;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.PatientSaveRequest;
import com.system.dto_vo.vo.PatientVO;
import com.system.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "就诊人管理", description = "患者维护自己的就诊人档案；管理员只读查询")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @PreAuthorize("hasRole('USER')")
    @Log(module = "就诊人管理", action = "用户查询我的就诊人列表")
    @Operation(summary = "我的就诊人列表")
    @GetMapping("/mine")
    public Result<List<PatientVO>> listMine() {
        List<PatientVO> patientVOS = patientService.listMine();
        return Result.ok(patientVOS);
    }

    @PreAuthorize("hasRole('USER')")
    @Log(module = "就诊人管理", action = "用户查询自己的就诊人")
    @Operation(summary = "用户查询就诊人")
    @GetMapping("/{id}")
    public Result<PatientVO> getMineById(@PathVariable Long id) {
        return Result.ok(patientService.getMineById(id));
    }


    @PreAuthorize("hasRole('USER')")
    @Log(module = "就诊人管理", action = "新增就诊人")
    @Operation(summary = "新增就诊人")
    @PostMapping
    public Result<PatientVO> create(@Valid @RequestBody PatientSaveRequest request) {
        return Result.ok(patientService.create(request));
    }

    @PreAuthorize("hasRole('USER')")
    @Log(module = "就诊人管理", action = "更新就诊人")
    @Operation(summary = "更新就诊人")
    @PutMapping("/{id}")
    public Result<PatientVO> update(@PathVariable Long id,
                                    @Valid @RequestBody PatientSaveRequest request) {
        request.setId(id);
        return Result.ok(patientService.update(request));
    }

    @PreAuthorize("hasRole('USER')")
    @Log(module = "就诊人管理", action = "删除就诊人")
    @Operation(summary = "删除就诊人")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        patientService.delete(id);
        return Result.ok();
    }

    @PreAuthorize("hasRole('USER')")
    @Log(module = "就诊人管理", action = "设置默认就诊人")
    @Operation(summary = "设置默认就诊人")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        patientService.setDefault(id);
        return Result.ok();
    }

}
