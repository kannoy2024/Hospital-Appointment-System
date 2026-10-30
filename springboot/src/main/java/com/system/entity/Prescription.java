package com.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prescription")
public class Prescription {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appointmentId;
    private Long userId;
    private Long doctorId;

    @TableField("total_amount_fen")
    private long totalAmountFen;

    private String status;
    private String payMethod;
    private LocalDateTime payTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
