package com.system.dto_vo.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 菜单可见角色分配请求：只改角色，不动菜单本身的其他字段。
 */
@Data
public class MenuRoleAssignRequest {

    @NotEmpty(message = "请至少选择一个角色")
    private List<String> roleCodes;

}
