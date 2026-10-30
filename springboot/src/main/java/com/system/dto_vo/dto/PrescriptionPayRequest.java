package com.system.dto_vo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PrescriptionPayRequest {

    @NotBlank(message = "请选择支付方式")
    private String payMethod;

}
