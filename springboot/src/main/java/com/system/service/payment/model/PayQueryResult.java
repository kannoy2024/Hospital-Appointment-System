package com.system.service.payment.model;

import lombok.Data;

/** 主动查询结果：用于回调丢失/超时兜底，绝不只依赖异步通知。 */
@Data
public class PayQueryResult {

    private String outTradeNo;

    /** 网关侧是否已支付 */
    private boolean paid;

    /** 网关侧交易号（Mock 阶段：MOCKTXN_xxx） */
    private String transactionId;

    /** 金额（单位：分） */
    private long amountFen;

    /** 网关侧原始状态字，如 SUCCESS / NOTPAY / CLOSED */
    private String tradeState;
}
