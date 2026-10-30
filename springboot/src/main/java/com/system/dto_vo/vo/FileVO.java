package com.system.dto_vo.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件上传结果")
public class FileVO {

    @Schema(description = "访问路径，如 /uploads/common/xxx.pdf")
    private String path;

    @Schema(description = "与 path 相同，用于前端展示")
    private String url;

    @Schema(description = "分类：avatar | common")
    private String category;

    @Schema(description = "原始文件名")
    private String originalFilename;

    @Schema(description = "文件大小（字节）")
    private long size;


}
