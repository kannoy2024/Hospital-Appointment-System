package com.system.service.payment.model;

import lombok.Data;

/** 退款结果。真实微信是异步的（受理返回 PROCESSING），Mock 阶段同步返回 SUCCESS。 */
@Data
public class RefundResult {

    /** 网关是否受理成功 */
    private boolean success;

    /** 网关侧退款单号 */
    private String refundId;

    /** PROCESSING / SUCCESS / FAILED */
    private String state;
}
