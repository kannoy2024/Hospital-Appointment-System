package com.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String email;

    private String phone;

    private Integer gender;

    /**
     * 头像访问路径
     */
    private String avatar;

    /**
     * 微信小程序 openid
     */
    private String openid;

    /**
     * 账户余额
     */
    @TableField("balance_fen")
    private long balanceFen;

    /**
     * 0-禁用 1-启用
     */
    private Integer status;

    /**
     * 角色编码，对应 sys_role.code
     */
    private String role;

    /**
     * 医生角色绑定的医生档案 ID
     */
    private Long doctorId;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long createUser;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateUser;


}
