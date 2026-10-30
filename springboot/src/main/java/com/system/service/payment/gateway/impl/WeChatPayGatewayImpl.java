package com.system.service.payment.gateway.impl;

import com.system.common.BusinessException;
import com.system.service.payment.gateway.PaymentGateway;
import com.system.service.payment.model.PayNotifyPayload;
import com.system.service.payment.model.PayQueryResult;
import com.system.service.payment.model.PrepayCommand;
import com.system.service.payment.model.PrepayResult;
import com.system.service.payment.model.RefundCommand;
import com.system.service.payment.model.RefundResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 微信支付通道：计划在 S5 实现（需要非个人主体小程序 + 商户号）。
 *
 * <p>S1 只保证 wechat.pay.channel=wechat 时应用能正常启动，不提供任何可用逻辑。
 * 换成真实实现时只需把下面五个方法体替换为 wechatpay-java 的调用，
 * PaymentService / PaymentBizHandler / Controller 一行都不用动 —— 这就是网关抽象的全部价值。
 */
@Component
@ConditionalOnProperty(name = "wechat.pay.channel", havingValue = "wechat")
public class WeChatPayGatewayImpl implements PaymentGateway {

    private static final String CHANNEL = "WECHAT";
    private static final String TODO_MSG = "微信支付通道未启用（计划在 S5 实现，需要商户资质）";

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public PrepayResult prepay(PrepayCommand cmd) {
        throw new BusinessException(TODO_MSG);
    }

    @Override
    public PayQueryResult query(String outTradeNo) {
        throw new BusinessException(TODO_MSG);
    }

    @Override
    public void close(String outTradeNo) {
        throw new BusinessException(TODO_MSG);
    }

    @Override
    public RefundResult refund(RefundCommand cmd) {
        throw new BusinessException(TODO_MSG);
    }

    @Override
    public RefundResult queryRefund(String outRefundNo) {
        throw new BusinessException(TODO_MSG);
    }

    @Override
    public PayNotifyPayload parseNotify(HttpServletRequest req) {
        throw new BusinessException(TODO_MSG);
    }
}
