package com.system.service.payment.gateway;

import com.system.service.payment.model.PayNotifyPayload;
import com.system.service.payment.model.PayQueryResult;
import com.system.service.payment.model.PrepayCommand;
import com.system.service.payment.model.PrepayResult;
import com.system.service.payment.model.RefundCommand;
import com.system.service.payment.model.RefundResult;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 支付网关：本项目与外部支付世界**唯一**的接触点。
 * 业务层永远不 import 任何微信 SDK；将来换通道只换本接口的实现类。
 */
public interface PaymentGateway {

    /** 通道标识：MOCK / WECHAT，写入 payment_order.channel */
    String channel();

    /** 下单，返回预支付参数 */
    PrepayResult prepay(PrepayCommand cmd);

    /** 主动查询（回调丢失时的兜底，绝不只依赖异步通知） */
    PayQueryResult query(String outTradeNo);

    /** 关单：超过 expire_time 必须关，否则真实微信侧仍可能被支付 */
    void close(String outTradeNo);

    /** 退款（真实微信是**异步**的：这里只负责受理，结果通过 refund_notify 回来） */
    RefundResult refund(RefundCommand cmd);

    /**
     * 查询退款结果。
     *
     * <p>退款卡在 REFUNDING 时必须先来问一句“到底退成了没”，
     * 不能盲目重提——真实微信同一 out_refund_no 虽然幂等，但盲目重试会掩盖真正的问题。
     */
    RefundResult queryRefund(String outRefundNo);

    /**
     * 解析回调：读原始 body + 验签 + 解密。
     * 参数故意是 HttpServletRequest 而不是反序列化好的 DTO —— 验签必须用原始字节，
     * 一旦经 Jackson 反序列化再 toString() 回来，签名必然不匹配（微信支付第一大坑）。
     */
    PayNotifyPayload parseNotify(HttpServletRequest req);
}
