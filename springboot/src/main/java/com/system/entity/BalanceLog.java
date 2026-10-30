package com.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
@Data
@TableName("balance_log")
public class BalanceLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;

    @TableField("amount_fen")
    private Long amountFen;

    @TableField("balance_after_fen")
    private long balanceAfterFen;
    private Long orderId;
    private String payMethod;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

}
