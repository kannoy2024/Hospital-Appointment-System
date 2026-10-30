package com.system.dto_vo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WalletRechargeRequest {

    @NotNull(message = "充值金额不能为空")
    @Min(value = 1, message = "充值金额至少1分")
    private Long amountFen;

    @NotBlank(message = "请选择支付方式")
    private String payMethod;


}
