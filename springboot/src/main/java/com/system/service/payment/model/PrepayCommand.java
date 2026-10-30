package com.system.service.payment.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 下单请求：业务层 → 网关。
 * 金额单位一律“分”。out_trade_no 由 PaymentService 生成后传入，网关不负责生成。
 */
@Data
public class PrepayCommand {

    /** 业务类型：APPOINTMENT / PRESCRIPTION / RECHARGE */
    private String bizType;

    /** 业务单 id */
    private Long bizId;

    /** 付款用户 id（落 payment_order.user_id，支付单查询的越权校验要用） */
    private Long userId;

    /** 商户支付单号（由 PaymentService 生成） */
    private String outTradeNo;

    /** 支付金额（单位：分） */
    private long amountFen;

    /** 支付方式：wechat / alipay / balance */
    private String payMethod;

    /** 商品描述，如 挂号费-张明华 */
    private String description;

    /** JSAPI 必传：付款人 openid；Mock 可空 */
    private String payerOpenid;

    /** 支付单过期时间；不传则由 PaymentService 按配置兜底 */
    private LocalDateTime expireTime;

    /**
     * 是否允许复用同一业务单未过期的 CREATED 支付单，默认允许。
     *
     * <p>充值必须传 false：充值没有业务表，bizId 只能填用户 id，
     * 若也走复用护栏，同一用户连续充两笔会被合并成一笔支付单，等于吞掉一笔。
     */
    private boolean reuseExistingCreated = true;
}
