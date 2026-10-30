package com.system.controller;

import com.system.common.Log;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.AppointmentCreateRequest;
import com.system.dto_vo.dto.AppointmentPayRequest;
import com.system.dto_vo.dto.AppointmentReviewRequest;
import com.system.dto_vo.vo.ConsultationRecordVO;
import com.system.dto_vo.vo.PrescriptionVO;
import com.system.service.ConsultationService;
import com.system.service.PrescriptionService;
import com.system.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.system.dto_vo.vo.AppointmentVO;
import com.system.dto_vo.vo.AppointmentPrepayVO;
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

@Tag(name = "患者预约", description = "患者预约与查询")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/appointments")
@AllArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final ConsultationService consultationService;
    private final PrescriptionService prescriptionService;

    @Log(module = "预约", action = "提交预约")
    @Operation(summary = "提交预约")
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public Result<AppointmentVO> create(@Valid @RequestBody AppointmentCreateRequest request) {
        return Result.ok(appointmentService.create(request));
    }

    @Operation(summary = "我的预约分页")
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public Result<PageResult<AppointmentVO>> pageMine(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String status) {
        return Result.ok(appointmentService.pageMine(current, size, status));
    }

    @Operation(summary = "预约详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public Result<AppointmentVO> getById(@PathVariable Long id) {
        return Result.ok(appointmentService.getMineById(id));
    }

    @Log(module = "预约", action = "支付预约")
    @Operation(summary = "支付预约（余额支付，立即完成）")
    @PostMapping("/{id}/pay")
    @PreAuthorize("hasRole('USER')")
    public Result<AppointmentVO> pay(@PathVariable Long id, @Valid @RequestBody AppointmentPayRequest request) {
        return Result.ok(appointmentService.payAppointment(id, request.getPayMethod()));
    }

    @Log(module = "预约", action = "发起支付")
    @Operation(summary = "发起预支付（微信/支付宝），返回调起支付所需参数；预约状态要等回调才变")
    @PostMapping("/{id}/prepay")
    @PreAuthorize("hasRole('USER')")
    public Result<AppointmentPrepayVO> prepay(@PathVariable Long id,
                                              @Valid @RequestBody AppointmentPayRequest request) {
        return Result.ok(appointmentService.prepay(id, request.getPayMethod()));
    }

    @Log(module = "预约", action = "取消预约")
    @Operation(summary = "取消预约")
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('USER')")
    public Result<Void> cancel(@PathVariable Long id) {
        appointmentService.cancelByUser(id);
        return Result.ok();
    }

    @Log(module = "预约", action = "到院签到")
    @Operation(summary = "到院签到")
    @PutMapping("/{id}/check-in")
    @PreAuthorize("hasRole('USER')")
    public Result<AppointmentVO> checkIn(@PathVariable Long id) {
        appointmentService.checkInByUser(id);
        return Result.ok(appointmentService.getMineById(id));
    }

    @Operation(summary = "查看病历")
    @GetMapping("/{id}/consultation")
    @PreAuthorize("hasRole('USER')")
    public Result<ConsultationRecordVO> consultation(@PathVariable Long id) {
        Long userId = com.system.security.SecurityUtils.getCurrentUserEntity().getId();
        appointmentService.getMineById(id);
        return Result.ok(consultationService.getVisibleForPatient(id, userId));
    }

    @Operation(summary = "查看处方")
    @GetMapping("/{id}/prescription")
    @PreAuthorize("hasRole('USER')")
    public Result<PrescriptionVO> prescription(@PathVariable Long id) {
        Long userId = com.system.security.SecurityUtils.getCurrentUserEntity().getId();
        appointmentService.getMineById(id);
        return Result.ok(prescriptionService.getVisibleForPatient(id, userId));
    }

    @Log(module = "预约", action = "评价医生")
    @Operation(summary = "提交就诊评价")
    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('USER')")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody AppointmentReviewRequest request) {
        appointmentService.submitReview(id, request);
        return Result.ok();
    }
}
