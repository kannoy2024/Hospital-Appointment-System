package com.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("medicine")
public class Medicine {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String spec;
    private String unit;

    @TableField("price_fen")
    private long priceFen;

    private Integer stock;
    private String usageDesc;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
