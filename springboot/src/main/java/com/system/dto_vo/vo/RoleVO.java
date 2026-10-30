package com.system.dto_vo.vo;

import com.system.entity.Role;
import lombok.Data;


@Data
public class RoleVO {

    private String code;
    private String name;

    public static RoleVO fromEntity(Role role) {
        RoleVO vo = new RoleVO();
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        return vo;
    }


}
