package com.system.dto_vo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DepartmentSaveRequest {

    private Long id;

    @NotBlank(message = "科室名称不能为空")
    private String name;

    private String description;
    private String location;
    private Integer sortOrder;

    @NotNull(message = "状态不能为空")
    private Integer status;


}
