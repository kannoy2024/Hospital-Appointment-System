package com.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 支付/退款回调流水（原文留存，幂等与排障的唯一凭据）。对应 payment_notify_log 表，12 列。 */
@Data
@TableName("payment_notify_log")
public class PaymentNotifyLog {

    public static final String TYPE_PAY    = "PAY";
    public static final String TYPE_REFUND = "REFUND";

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_DUP     = "DUP";
    public static final String RESULT_REFUND  = "REFUND";
    public static final String RESULT_ERROR   = "ERROR";

    /**
     * 退款回调送达正常、但网关告知「退款失败」。
     *
     * <p>与 {@link #RESULT_ERROR} 的区别很关键：ERROR 表示**本端没收下这条通知**
     * （验签失败 / 报文非法 / 找不到单），必须让网关重试；
     * 而 REFUND_FAILED 表示本端已正确记账，网关不需要再重试。
     */
    public static final String RESULT_REFUND_FAILED = "REFUND_FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** MOCK / WECHAT */
    private String channel;

    /** PAY 支付通知 / REFUND 退款通知 */
    private String notifyType;

    private String outTradeNo;
    private String outRefundNo;
    private String transactionId;

    /** 回调原始报文（务必原样保存，验签排障用） */
    private String rawBody;

    /** 验签是否通过：0 否 1 是（DB 为 tinyint） */
    private Integer signVerified;

    /** SUCCESS / DUP / REFUND / ERROR */
    private String processResult;

    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
