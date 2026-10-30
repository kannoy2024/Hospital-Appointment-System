package com.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 支付单（与业务表解耦，一个业务单可多次尝试）。对应 payment_order 表，19 列。 */
@Data
@TableName("payment_order")
public class PaymentOrder {

    // ===== 状态 / 通道常量：避免全项目散落字符串字面量 =====
    public static final String STATUS_CREATED   = "CREATED";
    public static final String STATUS_PAID      = "PAID";
    public static final String STATUS_REFUNDING = "REFUNDING";
    public static final String STATUS_REFUNDED  = "REFUNDED";
    public static final String STATUS_CLOSED    = "CLOSED";
    public static final String STATUS_FAILED    = "FAILED";

    public static final String BIZ_APPOINTMENT  = "APPOINTMENT";
    public static final String BIZ_PRESCRIPTION = "PRESCRIPTION";
    public static final String BIZ_RECHARGE     = "RECHARGE";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商户支付单号（网关约束：数字/字母/_/-/|/*，长 6~32，全局唯一） */
    private String outTradeNo;

    /** 业务类型：APPOINTMENT / PRESCRIPTION / RECHARGE */
    private String bizType;

    /** 业务单 id，同一业务单允许多个支付单（故意不唯一） */
    private Long bizId;

    private Long userId;

    @TableField("amount_fen")
    private long amountFen;

    private String currency;

    /** wechat / alipay / balance */
    private String payMethod;

    /** MOCK / WECHAT */
    private String channel;

    /** CREATED / PAID / REFUNDING / REFUNDED / CLOSED / FAILED */
    private String status;

    private String prepayId;

    /** 网关侧交易号：Mock 阶段由 MockPayGatewayImpl 自己造 */
    private String transactionId;

    private String payerOpenid;

    private String description;

    private LocalDateTime expireTime;
    private LocalDateTime paidTime;
    private LocalDateTime closeTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
