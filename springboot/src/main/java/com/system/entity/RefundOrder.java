package com.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 退款单。对应 refund_order 表，12 列。S1 只建结构与实体，S4 才写数据。 */
@Data
@TableName("refund_order")
public class RefundOrder {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS    = "SUCCESS";
    public static final String STATUS_FAILED     = "FAILED";
    public static final String STATUS_CLOSED     = "CLOSED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商户退款单号，唯一 */
    private String outRefundNo;

    /** 原商户支付单号 */
    private String outTradeNo;

    /** payment_order.id */
    private Long paymentOrderId;

    /** 收款用户 id（DDL 允许 NULL） */
    private Long userId;

    @TableField("amount_fen")
    private long amountFen;

    /** PROCESSING / SUCCESS / FAILED / CLOSED */
    private String status;

    private String reason;

    /** 网关侧退款单号 */
    private String refundId;

    private LocalDateTime successTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
