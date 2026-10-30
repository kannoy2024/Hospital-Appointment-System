package com.system.dto_vo.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class MenuSaveRequest {

    private Long id;

    @NotNull(message = "父菜单不能为空")
    private Long parentId = 0L;

    @NotBlank(message = "菜单名称不能为空")
    @Size(max = 50, message = "菜单名称不超过50个字符")
    private String name;

    @NotBlank(message = "路由路径不能为空")
    @Pattern(regexp = "^/admin/[a-zA-Z0-9/_-]+$", message = "路径须以 /admin/ 开头")
    private String path;

    @Size(max = 50, message = "图标名不超过50个字符")
    private String icon;

    @NotNull(message = "排序不能为空")
    private Integer sortOrder = 0;

    @NotEmpty(message="请至少选择一个角色")
    private List<String> roleCodes;


}
