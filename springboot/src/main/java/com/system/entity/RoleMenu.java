package com.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_role_menu")
public class RoleMenu {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码，如 ADMIN、USER；更多角色在 system.sql 中扩展 */
    private String roleCode;

    private Long menuId;

}
