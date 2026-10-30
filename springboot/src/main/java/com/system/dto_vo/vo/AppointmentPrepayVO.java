package com.system.dto_vo.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 预支付结果：小程序拿到它去调起支付。
 *
 * <p>payParams 的形态由支付通道决定，业务层不关心：
 * <ul>
 *   <li>Mock（S1/S2）：{@code {outTradeNo, cashierUrl}}，浏览器/小程序直接打开收银台；</li>
 *   <li>真实微信（S5）：{@code {appId, timeStamp, nonceStr, package, signType, paySign}}，喂给 wx.requestPayment。</li>
 * </ul>
 * 支付结果不在这里返回——必须由网关回调驱动，前端拿 outTradeNo 轮询
 * {@code GET /api/payments/{outTradeNo}}。
 */
@Data
public class AppointmentPrepayVO {

    /** 预约单 id */
    private Long appointmentId;

    /** 支付单号，前端用它轮询支付结果 */
    private String outTradeNo;

    /** 本次使用的支付方式：wechat / alipay */
    private String payMethod;

    /** 应付金额（单位：分） */
    private long amountFen;

    /** 预约当前状态（此刻一定是 unpaid） */
    private String appointmentStatus;

    /** 支付单过期时间，超过这个时间点不能再支付 */
    private LocalDateTime expireTime;

    /** 调起支付所需参数，形态由通道决定 */
    private Map<String, String> payParams;
}
