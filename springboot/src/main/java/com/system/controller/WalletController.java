package com.system.controller;

import com.system.common.PageResult;
import com.system.common.Result;
import com.system.config.OpenApiConfig;
import com.system.dto_vo.vo.BalanceLogVO;
import com.system.dto_vo.vo.PaymentPrepayVO;
import com.system.dto_vo.dto.WalletRechargeRequest;
import com.system.security.SecurityUtils;
import com.system.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "钱包", description = "余额充值与流水")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/wallet")
@AllArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "发起充值（返回支付参数，余额要等支付回调才增加）")
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/recharge")
    public Result<PaymentPrepayVO> recharge(@Valid @RequestBody WalletRechargeRequest request) {
        Long userId = SecurityUtils.getCurrentUserEntity().getId();
        return Result.ok(walletService.prepayRecharge(userId, request));
    }

    @Operation(summary = "我的余额流水")
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/logs")
    public Result<PageResult<BalanceLogVO>> logs(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        Long userId = SecurityUtils.getCurrentUserEntity().getId();
        return Result.ok(walletService.pageMyLogs(userId, current, size));
    }
}
