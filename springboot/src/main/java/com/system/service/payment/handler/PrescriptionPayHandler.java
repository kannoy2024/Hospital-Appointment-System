package com.system.service.payment.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.system.common.MoneyUtils;
import com.system.entity.PaymentOrder;
import com.system.entity.Prescription;
import com.system.mapper.PrescriptionMapper;
import com.system.service.NotificationService;
import com.system.service.payment.model.PayNotifyPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 处方缴费业务的支付回调处理器：把「药费到账」翻译成「处方已支付、可以去药房取药」。
 *
 * <p>与挂号的关键区别：处方没有「超时作废」的概念。
 * 支付单被关闭（用户取消 / 支付单过期）时，处方必须保持 unpaid，用户重新发起支付即可，
 * 所以 {@link #onPayClosed} 只记日志，绝不动业务状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrescriptionPayHandler implements PaymentBizHandler {

    private final PrescriptionMapper prescriptionMapper;
    private final NotificationService notificationService;

    @Override
    public String bizType() {
        return PaymentOrder.BIZ_PRESCRIPTION;
    }

    /**
     * 支付成功：处方 unpaid → paid，写支付方式与支付时间，并通知患者与开方医生。
     *
     * <p>预约保持 pending_rx_pay 不变，要等药房发药（dispense）才流转到 completed。
     *
     * @param order   已置 PAID 的支付单
     * @param payload 回调载荷
     */
    @Override
    public void markPaid(PaymentOrder order, PayNotifyPayload payload) {
        Long prescriptionId = order.getBizId();
        Prescription prescription = prescriptionMapper.selectById(prescriptionId);
        if (prescription == null) {
            log.error("[PayRx] 支付单已 PAID，但处方不存在，需人工核查：outTradeNo={} prescriptionId={}",
                    order.getOutTradeNo(), prescriptionId);
            return;
        }

        // CAS：只有 unpaid 才能推进，天然幂等，也不会把已作废/已取药的处方改回 paid
        int affected = prescriptionMapper.update(null, new LambdaUpdateWrapper<Prescription>()
                .eq(Prescription::getId, prescriptionId)
                .eq(Prescription::getStatus, "unpaid")
                .set(Prescription::getStatus, "paid")
                .set(Prescription::getPayMethod, order.getPayMethod())
                .set(Prescription::getPayTime, LocalDateTime.now()));

        if (affected == 0) {
            // 钱已收到、处方却没推进 —— 收钱未推进业务，必须显式告警而不是静默
            log.error("[PayRx] ★异常：支付单已 PAID，但处方[{}]当前状态为[{}]，"
                            + "收钱未推进业务，已交由对账任务补救退款：outTradeNo={}",
                    prescriptionId, prescription.getStatus(), order.getOutTradeNo());
            return;
        }

        long totalFen = prescription.getTotalAmountFen();
        notificationService.notifyUser(
                prescription.getUserId(),
                "prescription_paid",
                "药费支付成功",
                String.format("处方药费 %s 已支付成功，请前往药房取药，取药后由医院确认。",
                        MoneyUtils.formatYuan(totalFen)),
                "appointment",
                prescription.getAppointmentId());
        notificationService.notifyDoctor(
                prescription.getDoctorId(),
                "prescription_paid_doctor",
                "患者已缴费",
                String.format("患者已支付处方药费 %s，请核对后标记取药。", MoneyUtils.formatYuan(totalFen)),
                "appointment",
                prescription.getAppointmentId());

        log.info("[PayRx] 处方{}支付成功，unpaid → paid，payMethod={}",
                prescriptionId, order.getPayMethod());
    }

    /**
     * 支付单被关闭（用户取消 / 过期）：处方没有「过期作废」语义，业务状态一律不动。
     *
     * @param order 已置 CLOSED 的支付单
     */
    @Override
    public void onPayClosed(PaymentOrder order) {
        log.info("[PayRx] 处方支付单已关闭，处方保持 unpaid，用户可重新发起支付：outTradeNo={} prescriptionId={}",
                order.getOutTradeNo(), order.getBizId());
    }

    /**
     * 这笔已支付的药费单是否已经「白收」了：对账任务据此发现后发起补救退款。
     *
     * <p>三种情况收的钱都应该退回去：
     * <ol>
     *   <li>处方不存在（业务都找不到了）；</li>
     *   <li>处方已作废（cancelled）；</li>
     *   <li>处方已用<b>余额</b>付清 —— 说明这笔微信单是 {@code payPrescription} 之后残留的重复支付。</li>
     * </ol>
     *
     * <p>注意：处方状态为 paid 且支付方式不是余额时一律返回 false。
     * 那种情况多为「同一处方开了两张微信单、用户都付了」，本端无法从数据上判断哪一张是
     * 真正推进业务的那一张，贸然退款会把正确的那笔退掉、留下错误的那笔，所以交给人工核对。
     *
     * @param order 状态为 PAID 的支付单
     * @return 这笔收款已无业务价值时返回 true
     */
    @Override
    public boolean isBizCancelled(PaymentOrder order) {
        Prescription prescription = prescriptionMapper.selectById(order.getBizId());
        if (prescription == null) {
            return true;
        }
        if ("cancelled".equals(prescription.getStatus())) {
            return true;
        }
        return "paid".equals(prescription.getStatus())
                && "balance".equals(prescription.getPayMethod());
    }
}
