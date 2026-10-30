package com.system.dto_vo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PrescriptionItemRequest {

    @NotNull(message = "药品不能为空")
    private Long medicineId;

    private String dosage;

    private String frequency;

    private Integer days;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为1")
    private Integer quantity;


}
