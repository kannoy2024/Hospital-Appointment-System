package com.system.dto_vo.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 通用预支付返回体：业务侧只关心「这单多少钱、付完通知我」，钱怎么收由支付模块决定。
 *
 * <p>挂号沿用历史契约 {@link AppointmentPrepayVO}（带 appointmentId），
 * 处方缴费与钱包充值走本类。金额单位为「分」，前端展示前需除以 100。
 */
@Data
public class PaymentPrepayVO {

    /** 业务类型：APPOINTMENT / PRESCRIPTION / RECHARGE */
    private String bizType;

    /** 业务单 id（充值没有业务表，此字段填用户 id） */
    private Long bizId;

    /** 商户支付单号，前端用它轮询 GET /api/payments/{outTradeNo} 确认支付结果 */
    private String outTradeNo;

    /** 支付方式：wechat / alipay */
    private String payMethod;

    /** 待支付金额（单位：分） */
    private long amountFen;

    /** 发起支付时业务单的状态，如 unpaid */
    private String bizStatus;

    /** 支付单过期时间，超过后需重新发起支付 */
    private LocalDateTime expireTime;

    /** 调起支付所需参数；Mock 通道下是收银台地址 cashierUrl */
    private Map<String, String> payParams;
}
