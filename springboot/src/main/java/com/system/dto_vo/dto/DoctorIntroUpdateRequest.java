package com.system.dto_vo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "医生更新个人简介")
public class DoctorIntroUpdateRequest {

    @Schema(description = "简介描述")
    @Size(max = 1000, message = "简介长度不能超过1000个字符")
    private String intro;


}
