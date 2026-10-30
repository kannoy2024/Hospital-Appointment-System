package com.system.service.payment.handler;

import com.system.entity.PaymentOrder;
import com.system.service.payment.model.PayNotifyPayload;

/**
 * 业务侧唯一需要实现的东西：挂号 / 处方 / 充值 各写一个实现类即可接入支付。
 *
 * <p>S1 不提供任何实现（挂号在 S2、处方与充值在 S3），
 * PaymentService 通过 Spring 收集全部实现成 Map&lt;bizType, handler&gt;，回调时按支付单的 bizType 派发。
 */
public interface PaymentBizHandler {

    /** 本 handler 负责的业务类型：APPOINTMENT / PRESCRIPTION / RECHARGE */
    String bizType();

    /**
     * 支付成功：把业务单推进到“已支付”。
     * 由 PaymentService 在**同一个事务**内、支付单 CAS 成功之后调用。
     */
    void markPaid(PaymentOrder order, PayNotifyPayload payload);

    /**
     * 支付单被关闭（超时/用户取消）：业务侧回滚，例如取消预约并回补号源。
     * 由 PaymentService.closeIfCreated() 在支付单 CAS 成功之后调用。
     */
    void onPayClosed(PaymentOrder order);

    /**
     * 这笔已支付的款项是否已经「白收」了：业务单作废，或已经被其它方式结清。
     *
     * <p>对账任务用它发现「钱已经收了、业务却不需要这笔钱」的记录并发起补救退款，覆盖两类漏网场景：
     * <ol>
     *   <li>业务单已作废（预约被取消、处方被作废），而取消时的退款调用抛异常被业务侧吞掉；</li>
     *   <li>业务单已被<b>余额</b>结清，{@code prepay} 留下的网关支付单却没关掉、被用户重复支付。</li>
     * </ol>
     *
     * <p>实现方必须谨慎：返回 true 会真的把钱退回去，只能在这些钱确实没有对应业务价值时才返回 true。
     * 拿不准的情况（例如同一业务开了两张网关单、用户都付了，无法判断哪张推进了业务）应返回 false 交给人工核对。
     *
     * <p>默认 false：没有作废概念、也不会被重复支付的业务（如充值）不需要实现。
     */
    default boolean isBizCancelled(PaymentOrder order) {
        return false;
    }
}
