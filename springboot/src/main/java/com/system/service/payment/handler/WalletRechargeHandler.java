package com.system.service.payment.handler;

import com.system.entity.PaymentOrder;
import com.system.service.WalletService;
import com.system.service.payment.model.PayNotifyPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 钱包充值业务的支付回调处理器：钱真正到账之后才加余额。
 *
 * <p>本类存在的意义就是消灭「假装充值成功」——改造前 {@code WalletServiceImpl#recharge}
 * 收到请求就 {@code credit()} 加余额，等于没收到钱先给钱。现在唯一的加余额入口是支付回调。
 *
 * <p>为什么用 {@link ObjectProvider} 延迟拿 {@code WalletService}：
 * 依赖链 {@code WalletService → PaymentService → List<PaymentBizHandler> → 本类 → WalletService}
 * 是一个环，构造期强依赖会让 Spring 启动直接报循环依赖；改成调用时才解析即可断开。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletRechargeHandler implements PaymentBizHandler {

    private final ObjectProvider<WalletService> walletServiceProvider;

    @Override
    public String bizType() {
        return PaymentOrder.BIZ_RECHARGE;
    }

    /**
     * 支付成功：按支付单金额入账。
     *
     * <p>★金额只信支付单：不读回调里的金额（已被 PaymentServiceImpl 校验过），更不读前端传值。
     *
     * @param order   已置 PAID 的支付单
     * @param payload 回调载荷
     */
    @Override
    public void markPaid(PaymentOrder order, PayNotifyPayload payload) {
        walletServiceProvider.getObject().applyRecharge(
                order.getUserId(), order.getId(), order.getAmountFen(), order.getPayMethod());
        log.info("[PayRecharge] 充值到账 userId={} amountFen={} payMethod={} outTradeNo={}",
                order.getUserId(), order.getAmountFen(), order.getPayMethod(), order.getOutTradeNo());
    }

    /**
     * 支付单被关闭：用户没付钱，余额一分都不能动。
     *
     * @param order 已置 CLOSED 的支付单
     */
    @Override
    public void onPayClosed(PaymentOrder order) {
        log.info("[PayRecharge] 充值单已关闭，余额不变：outTradeNo={} userId={} amountFen={}",
                order.getOutTradeNo(), order.getUserId(), order.getAmountFen());
    }
}
