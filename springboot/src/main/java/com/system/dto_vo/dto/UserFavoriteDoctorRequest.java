package com.system.dto_vo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "医生收藏请求")
public class UserFavoriteDoctorRequest {

    @Schema(description = "要收藏的医生ID")
    @NotNull(message = "医生ID不能为空")
    private Long doctorId;
}