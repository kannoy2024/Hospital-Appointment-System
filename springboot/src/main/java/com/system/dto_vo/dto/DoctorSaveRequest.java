package com.system.dto_vo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DoctorSaveRequest {

    private Long id;

    @NotNull(message = "科室不能为空")
    private Long departmentId;

    @NotBlank(message = "医生姓名不能为空")
    private String name;

    private String title;

    @NotNull(message = "性别不能为空")
    private Integer gender;

    private String avatar;
    private String intro;

    @NotNull(message = "状态不能为空")
    private Integer status;


}
