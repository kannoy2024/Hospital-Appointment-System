package com.system.controller;

import com.system.common.Log;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.ConsultationSaveRequest;
import com.system.dto_vo.dto.PrescriptionCreateRequest;
import com.system.dto_vo.vo.*;
import com.system.service.DoctorWorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.system.common.PageResult;
import com.system.dto_vo.dto.DoctorIntroUpdateRequest;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

import com.system.service.MedicineService;

@Tag(name = "医生工作台", description = "医生登录后查看本人排班,预约,接诊患者,开局处方")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/doctor")
@PreAuthorize("hasRole('DOCTOR')")
@AllArgsConstructor
public class DoctorWorkspaceController {

    private final DoctorWorkspaceService doctorWorkspaceService;
    private final MedicineService medicineService;

    @Operation(summary = "我的医生档案")
    @GetMapping("/me")
    public Result<DoctorVO> me() {
        return Result.ok(doctorWorkspaceService.getMyProfile());
    }

    @Log(module = "医生工作台", action = "更新个人简介")
    @Operation(summary = "更新个人简介")
    @PutMapping("/me/intro")
    public Result<DoctorVO> updateIntro(@Valid @RequestBody DoctorIntroUpdateRequest request) {
        return Result.ok(doctorWorkspaceService.updateMyIntro(request.getIntro()));
    }

    @Operation(summary = "工作概览统计")
    @GetMapping("/stats")
    public Result<DoctorWorkspaceStatsVO> stats() {
        return Result.ok(doctorWorkspaceService.getStats());
    }

    @Log(module = "医生工作台", action = "查询医生排班分页")
    @Operation(summary = "我的排班分页")
    @GetMapping("/schedules")
    public Result<PageResult<ScheduleVO>> schedules(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDateStart,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDateEnd) {
        return Result.ok(doctorWorkspaceService.pageMySchedules(current, size, scheduleDateStart,scheduleDateEnd));
    }

    @Operation(summary = "我的预约分页")
    @GetMapping("/appointments")
    public Result<PageResult<AppointmentVO>> appointments(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduleDate) {
        return Result.ok(doctorWorkspaceService.pageMyAppointments(current, size, status, scheduleDate));
    }

    @Operation(summary = "启用药品列表")
    @GetMapping("/medicines")
    public Result<List<MedicineVO>> medicines(@RequestParam(required = false) String keyword) {
        return Result.ok(medicineService.listActive(keyword));
    }

    @Log(module = "医生工作台", action = "开始接诊")
    @Operation(summary = "开始接诊")
    @PutMapping("/appointments/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        doctorWorkspaceService.startAppointment(id);
        return Result.ok();
    }

    @Log(module = "医生工作台", action = "保存病历")
    @Operation(summary = "保存病历")
    @PutMapping("/appointments/{id}/consultation")
    public Result<ConsultationRecordVO> saveConsultation(
            @PathVariable Long id,
            @Valid @RequestBody ConsultationSaveRequest request) {
        return Result.ok(doctorWorkspaceService.saveConsultation(id, request));
    }

    @Log(module = "医生工作台", action = "开具处方")
    @Operation(summary = "开具处方")
    @PostMapping("/appointments/{id}/prescription")
    public Result<PrescriptionVO> createPrescription(
            @PathVariable Long id,
            @Valid @RequestBody PrescriptionCreateRequest request) {
        return Result.ok(doctorWorkspaceService.createPrescription(id, request));
    }

    @Operation(summary = "诊疗详情")
    @GetMapping("/appointments/{id}/clinical")
    public Result<ClinicalBundleVO> clinical(@PathVariable Long id) {
        return Result.ok(doctorWorkspaceService.getClinical(id));
    }

    @Log(module = "医生工作台", action = "标记就诊完成")
    @Operation(summary = "标记就诊完成")
    @PutMapping("/appointments/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        doctorWorkspaceService.completeAppointment(id);
        return Result.ok();
    }

    @Log(module = "医生工作台", action = "标记取药")
    @Operation(summary = "标记取药")
    @PutMapping("/appointments/{id}/dispense")
    public Result<Void> dispense(@PathVariable Long id) {
        doctorWorkspaceService.dispensePrescription(id);
        return Result.ok();
    }
}
