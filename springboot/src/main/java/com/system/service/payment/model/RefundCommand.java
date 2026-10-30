package com.system.service.payment.model;

import lombok.Data;

/** 退款请求。outRefundNo 由调用方生成，必须唯一。 */
@Data
public class RefundCommand {

    /** 原商户支付单号 */
    private String outTradeNo;

    /** 商户退款单号（唯一），可用“原单号-R1” */
    private String outRefundNo;

    /** 退款金额（单位：分） */
    private long amountFen;

    /** 退款原因 */
    private String reason;
}
