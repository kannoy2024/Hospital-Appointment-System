package com.system.controller.admin;

import com.system.common.Log;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.service.PrescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "处方管理", description = "管理员处方取药管理")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/admin/prescriptions")
@PreAuthorize("hasRole('ADMIN')")
@AllArgsConstructor
public class PrescriptionAdminController {

    private final PrescriptionService prescriptionService;

    @Log(module = "处方管理", action = "标记取药")
    @Operation(summary = "标记已取药")
    @PutMapping("/{id}/dispense")
    public Result<Void> dispense(@PathVariable Long id) {
        prescriptionService.dispensePrescription(id);
        return Result.ok();
    }
}
