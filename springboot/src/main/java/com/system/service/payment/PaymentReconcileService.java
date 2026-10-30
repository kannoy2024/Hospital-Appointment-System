package com.system.service.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.system.entity.PaymentOrder;
import com.system.entity.RefundOrder;
import com.system.mapper.PaymentOrderMapper;
import com.system.mapper.RefundOrderMapper;
import com.system.service.payment.gateway.PaymentGateway;
import com.system.service.payment.model.PayQueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对账/兜底任务：绝不只依赖异步通知。
 *
 * <p>三件事，每件都对应一种“钱和业务对不上”的真实故障：
 * <ol>
 *   <li>{@link #reconcileExpiredOrders}：CREATED 且已过期 → 先问网关，确认未付才关单；</li>
 *   <li>{@link #reconcileStuckRefunding}：卡在 REFUNDING 超过 N 分钟 → 问网关结果或重提退款；</li>
 *   <li>{@link #reconcilePaidButBizCancelled}：钱收了但业务单已取消 → 补救退款。</li>
 * </ol>
 *
 * <p>注意：本类**不**负责取消业务单（预约/处方），那是各业务自己的超时任务，
 * 它们会调用 {@link PaymentService#closeIfCreated(String)} 来先关单、再回滚业务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconcileService {

    /** 单次扫描上限，避免一次拉起过多远程查询 */
    private static final int BATCH_SIZE = 200;

    private final PaymentOrderMapper paymentOrderMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final PaymentService paymentService;
    private final PaymentGateway paymentGateway;

    /** REFUNDING 状态超过多少分钟没动静就视为卡住（必须大于网关退款受理的常规耗时） */
    @Value("${wechat.pay.reconcile-refunding-minutes:5}")
    private int refundingTimeoutMinutes;

    @Scheduled(fixedDelayString = "${wechat.pay.reconcile-interval-ms:60000}")
    public void reconcile() {
        reconcileExpiredOrders();
        reconcileStuckRefunding();
        reconcilePaidButBizCancelled();
    }

    /** ① 过期未支付的单：先问网关，确认未付才关（用户可能刚好在过期瞬间付成功） */
    private void reconcileExpiredOrders() {
        List<PaymentOrder> expired = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_CREATED)
                .isNotNull(PaymentOrder::getExpireTime)
                .lt(PaymentOrder::getExpireTime, LocalDateTime.now())
                .orderByAsc(PaymentOrder::getId)
                .last("LIMIT " + BATCH_SIZE));
        if (expired.isEmpty()) {
            return;
        }

        log.info("[Reconcile] 发现 {} 条已过期的 CREATED 支付单", expired.size());
        int closed = 0;
        for (PaymentOrder order : expired) {
            try {
                // 先问网关：用户可能刚好在过期瞬间付成功，回调还在路上 —— 这种情况下不能直接关
                PayQueryResult query = paymentGateway.query(order.getOutTradeNo());
                if (query.isPaid()) {
                    log.warn("[Reconcile] 支付单已过期但网关显示已支付，等待回调或人工介入 outTradeNo={}",
                            order.getOutTradeNo());
                    continue;
                }
                if (paymentService.closeIfCreated(order.getOutTradeNo())) {
                    closed++;
                }
            } catch (Exception e) {
                log.error("[Reconcile] 关单失败 outTradeNo={}", order.getOutTradeNo(), e);
            }
        }
        log.info("[Reconcile] 过期关单完成：扫描 {} 条，关闭 {} 条", expired.size(), closed);
    }

    /** ② 卡在 REFUNDING 的单：交给 PaymentService.retryRefund 做“问结果 / 重提”两段式收敛 */
    private void reconcileStuckRefunding() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(refundingTimeoutMinutes);
        List<PaymentOrder> stuck = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_REFUNDING)
                .lt(PaymentOrder::getUpdateTime, deadline)
                .orderByAsc(PaymentOrder::getId)
                .last("LIMIT " + BATCH_SIZE));
        if (stuck.isEmpty()) {
            return;
        }

        log.info("[Reconcile] 发现 {} 条卡在 REFUNDING 的支付单", stuck.size());
        int settled = 0;
        for (PaymentOrder order : stuck) {
            try {
                RefundOrder refund = refundOrderMapper.selectOne(new LambdaQueryWrapper<RefundOrder>()
                        .eq(RefundOrder::getOutTradeNo, order.getOutTradeNo())
                        .orderByDesc(RefundOrder::getId)
                        .last("LIMIT 1"));
                if (refund == null) {
                    // REFUNDING 却没有退款单，属于不该出现的状态，留给人工而不是自动猜
                    log.error("[Reconcile] ★支付单 REFUNDING 但找不到退款单，请人工核查 outTradeNo={}",
                            order.getOutTradeNo());
                    continue;
                }
                if (paymentService.retryRefund(refund.getOutRefundNo())) {
                    settled++;
                }
            } catch (Exception e) {
                log.error("[Reconcile] 退款重试异常 outTradeNo={}", order.getOutTradeNo(), e);
            }
        }
        log.info("[Reconcile] 退款重试完成：扫描 {} 条，收敛 {} 条", stuck.size(), settled);
    }

    /** ③ 钱收了、业务单却已取消：补救退款（覆盖“取消时退款调用抛异常被吞掉”的漏网记录） */
    private void reconcilePaidButBizCancelled() {
        int refunded = paymentService.sweepPaidButBizCancelled(BATCH_SIZE);
        if (refunded > 0) {
            log.warn("[Reconcile] 本轮补救退款 {} 条「已收款但业务已取消」的支付单", refunded);
        }
    }
}
