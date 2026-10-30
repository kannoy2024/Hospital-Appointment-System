package com.system.dto_vo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
@Data
public class ScheduleSaveRequest {

    private Long id;

    @NotNull(message = "医生不能为空")
    private Long doctorId;

    @NotNull(message = "出诊日期不能为空")
    private LocalDate scheduleDate;

    @NotBlank(message = "时段不能为空")
    private String timeSlot;

    @NotNull(message = "总号源不能为空")
    private Integer totalQuota;

    @NotNull(message = "状态不能为空")
    private Integer status;

}
