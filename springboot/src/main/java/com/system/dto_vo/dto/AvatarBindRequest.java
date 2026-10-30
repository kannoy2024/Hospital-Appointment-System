package com.system.dto_vo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Schema(description = "绑定头像请求")
@Data
public class AvatarBindRequest {

    @Schema(description = "上传接口返回的 path", example = "/uploads/avatar/1_1710000000000.jpg")
    @NotBlank(message = "文件路径不能为空")
    @Pattern(regexp = "^/uploads/avatar/[0-9]+_.+", message = "无效的头像路径")
    private String path;

}
