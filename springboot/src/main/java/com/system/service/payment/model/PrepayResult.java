package com.system.service.payment.model;

import lombok.Data;

import java.util.Map;

/** 下单结果：网关 → 业务层。 */
@Data
public class PrepayResult {

    /** 网关预支付 id（Mock 阶段自己造，如 MOCK_PREPAY_xxx） */
    private String prepayId;

    /** 支付单号，回显给前端便于轮询 */
    private String outTradeNo;

    /**
     * 前端调起支付所需参数。
     * 真实微信是 {appId,timeStamp,nonceStr,package,signType,paySign} 六项；
     * Mock 阶段给 {outTradeNo, cashierUrl}，浏览器直接打开收银台。
     */
    private Map<String, String> payParams;
}
