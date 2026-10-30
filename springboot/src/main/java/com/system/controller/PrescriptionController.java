package com.system.controller;

import com.system.common.Log;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.dto.PrescriptionPayRequest;
import com.system.dto_vo.vo.PaymentPrepayVO;
import com.system.dto_vo.vo.PrescriptionVO;
import com.system.security.SecurityUtils;
import com.system.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "处方", description = "患者端处方支付")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/prescriptions")
@PreAuthorize("hasRole('USER')")
@AllArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;



    @Operation(summary = "处方详情")
    @GetMapping("/{id}")
    public Result<PrescriptionVO> getById(@PathVariable Long id) {
        PrescriptionVO vo = prescriptionService.getById(id);
        if (!vo.getUserId().equals(SecurityUtils.getCurrentUserEntity().getId())) {
            return Result.fail(403, "无权查看该处方");
        }
        return Result.ok(vo);
    }

    @Log(module = "处方", action = "支付处方")
    @Operation(summary = "支付处方（余额支付，立即完成）")
    @PostMapping("/{id}/pay")
    public Result<PrescriptionVO> pay(@PathVariable Long id, @Valid @RequestBody PrescriptionPayRequest request) {
        Long userId = SecurityUtils.getCurrentUserEntity().getId();
        return Result.ok(prescriptionService.payPrescription(id, userId, request.getPayMethod()));
    }

    @Log(module = "处方", action = "发起缴费")
    @Operation(summary = "发起处方费预支付（微信/支付宝），返回调起支付参数")
    @PostMapping("/{id}/prepay")
    public Result<PaymentPrepayVO> prepay(@PathVariable Long id,
                                          @Valid @RequestBody PrescriptionPayRequest request) {
        Long userId = SecurityUtils.getCurrentUserEntity().getId();
        return Result.ok(prescriptionService.prepay(id, userId, request.getPayMethod()));
    }
}
