package com.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码，如 ADMIN、USER；更多角色在 system.sql 中扩展 */
    private String code;

    private String name;

    private Integer sortOrder;

    /** 0禁用 1启用 */
    private Integer status;

}
