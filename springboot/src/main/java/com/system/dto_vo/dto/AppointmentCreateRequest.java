package com.system.dto_vo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppointmentCreateRequest {

    @NotNull(message = "排班不能为空")
    private Long scheduleId;

    private Long patientId;

    @Deprecated
    private String patientName;
    @Deprecated
    private String patientPhone;

    private String remark;

    @Min(value = 1, message = "挂号费至少1分")
    private Long amountFen;

}
