package com.system.entity;


import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;


//就诊人实体类
@Data
@TableName("patient")
public class Patient {


    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String phone;

    //身份证号码
    private String idCard;

    //性别 1男 2女 0未知
    private Integer gender;

    //出生日期
    private LocalDate birthDate;

    //民族编号
    private Integer nation;

    //地址
    private String address;

    //婚否 1已婚 2未婚
    private Integer marital;

    //是否默认就诊人 1是 0否
    private Integer isDefault;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;


    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
