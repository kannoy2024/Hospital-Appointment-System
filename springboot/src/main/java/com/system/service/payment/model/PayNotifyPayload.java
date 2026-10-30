package com.system.service.payment.model;

import lombok.Data;

/** 解析后的回调载荷：验签 + 解密已完成，业务层直接可用。 */
@Data
public class PayNotifyPayload {

    /** PAY 支付通知 / REFUND 退款通知 */
    private String notifyType;

    private String outTradeNo;

    private String outRefundNo;

    private String transactionId;

    /** 金额（单位：分） */
    private long amountFen;

    /** 支付通知：是否支付成功；退款通知：是否退款成功 */
    private boolean success;

    /** 验签是否通过 */
    private boolean signVerified;

    /** 验签/解析失败原因（落 payment_notify_log.error_msg） */
    private String errorMsg;

    /** 回调原始报文，原样入库 */
    private String rawBody;
}
