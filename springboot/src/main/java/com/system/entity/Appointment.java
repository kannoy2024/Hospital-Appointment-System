package com.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("appointment")
public class Appointment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long patientId;
    private Long scheduleId;
    private String patientName;
    private String patientPhone;

    @TableField("amount_fen")
    private long amountFen;


    private String payMethod;
    private LocalDateTime payTime;
    //    unpaid/pending/checked_in/in_consultation/pending_rx_pay/completed/cancelled
    //    未支付/待处理/已登记/咨询中/待支付处方/已完成/已取消
    private String status;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
