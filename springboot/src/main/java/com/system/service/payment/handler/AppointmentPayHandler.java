package com.system.service.payment.handler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.system.common.RedisConstants;
import com.system.common.ResilientRedisClient;
import com.system.entity.Appointment;
import com.system.entity.Department;
import com.system.entity.Doctor;
import com.system.entity.PaymentOrder;
import com.system.entity.Schedule;
import com.system.mapper.AppointmentMapper;
import com.system.mapper.DepartmentMapper;
import com.system.mapper.DoctorMapper;
import com.system.mapper.ScheduleMapper;
import com.system.service.NotificationService;
import com.system.service.ScheduleCacheService;
import com.system.service.payment.model.PayNotifyPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 挂号业务的支付回调处理器：把“钱到账了”翻译成“预约可以就诊了”。
 *
 * <p>这是挂号链路唯一被允许把预约从 unpaid 推进到 pending 的地方——
 * 支付状态一律由支付单驱动，任何业务代码都不能自己“假装支付成功”。
 *
 * <p>两个钩子的职责边界：
 * <ul>
 *   <li>{@link #markPaid}：支付单 CREATED → PAID 之后调用，推进预约 unpaid → pending；</li>
 *   <li>{@link #onPayClosed}：支付单 CREATED → CLOSED 之后调用，回滚预约 unpaid → cancelled 并回补号源。</li>
 * </ul>
 * 两者都用 {@code WHERE status='unpaid'} 的 CAS 更新，因此天然幂等、
 * 且不会把已经取消或已完成的预约误改回 pending。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentPayHandler implements PaymentBizHandler {

    private final AppointmentMapper appointmentMapper;
    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final ScheduleCacheService scheduleCacheService;
    private final NotificationService notificationService;
    private final ResilientRedisClient resilientRedisClient;

    @Override
    public String bizType() {
        return PaymentOrder.BIZ_APPOINTMENT;
    }

    /**
     * 支付成功：预约 unpaid → pending，写支付方式与支付时间，摘掉延迟队列，通知用户。
     *
     * @param order   已置 PAID 的支付单
     * @param payload 回调载荷
     */
    @Override
    public void markPaid(PaymentOrder order, PayNotifyPayload payload) {
        Long appointmentId = order.getBizId();
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            log.error("[PayAppointment] 支付单已 PAID，但预约不存在，需人工核查：outTradeNo={} appointmentId={}",
                    order.getOutTradeNo(), appointmentId);
            return;
        }

        // CAS：只有 unpaid 才能推进。已取消/已完成的预约绝不能被一笔迟到的回调改回 pending
        int affected = appointmentMapper.update(null, new LambdaUpdateWrapper<Appointment>()
                .eq(Appointment::getId, appointmentId)
                .eq(Appointment::getStatus, "unpaid")
                .set(Appointment::getStatus, "pending")
                .set(Appointment::getPayMethod, order.getPayMethod())
                .set(Appointment::getPayTime, LocalDateTime.now()));

        if (affected == 0) {
            // 钱已经收到、业务单却没推进 —— 超时取消与支付成功撞车的资损点，必须显式告警而不是静默。
            // 预约已是 cancelled 时，PaymentReconcileService.sweepPaidButBizCancelled() 会发现并自动退款。
            log.error("[PayAppointment] ★异常：支付单已 PAID，但预约[{}]当前状态为[{}]，收钱未推进业务，"
                            + "已交由对账任务补救退款：outTradeNo={}",
                    appointmentId, appointment.getStatus(), order.getOutTradeNo());
            return;
        }

        // 已支付就立刻从延迟队列摘掉，避免超时任务在边界时刻再扫到它
        removeFromDelayQueue(appointmentId);

        notificationService.notifyUser(
                appointment.getUserId(),
                "appointment_paid",
                "预约成功",
                "您已成功预约" + describe(appointment) + "，请按时到院签到。",
                "appointment",
                appointmentId);

        log.info("[PayAppointment] 预约{}支付成功，unpaid → pending，payMethod={}",
                appointmentId, order.getPayMethod());
    }

    /**
     * 支付单被关闭（超时 / 用户取消）：预约 unpaid → cancelled，回补 MySQL 与 Redis 号源。
     *
     * <p>调用方必须先关支付单再改业务，本方法只负责“业务侧回滚”这一段。
     *
     * @param order 已置 CLOSED 的支付单
     */
    @Override
    public void onPayClosed(PaymentOrder order) {
        Long appointmentId = order.getBizId();
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            log.warn("[PayAppointment] 支付单关闭，但预约不存在：appointmentId={}", appointmentId);
            return;
        }

        int affected = appointmentMapper.update(null, new LambdaUpdateWrapper<Appointment>()
                .eq(Appointment::getId, appointmentId)
                .eq(Appointment::getStatus, "unpaid")
                .set(Appointment::getStatus, "cancelled"));

        if (affected == 0) {
            // 已经支付成功过（回调抢先），这里必须什么都不做，否则会把已付款的预约取消掉
            log.info("[PayAppointment] 预约{}当前状态为[{}]，无需回滚",
                    appointmentId, appointment.getStatus());
            return;
        }

        scheduleMapper.decrementBookedCount(appointment.getScheduleId());
        scheduleCacheService.releaseSlot(appointment.getScheduleId());
        removeFromDelayQueue(appointmentId);

        notificationService.notifyUser(
                appointment.getUserId(),
                "appointment_cancelled",
                "预约已取消",
                "您的预约" + describe(appointment) + "已取消，号源已释放。",
                "appointment",
                appointmentId);

        log.info("[PayAppointment] 支付单关闭 → 预约{}已取消并回补号源", appointmentId);
    }

    // ==================== 内部工具 ====================

    /**
     * 这笔支付是否已经「白收」了：对账任务据此发现后发起补救退款。
     *
     * <p>三种情况收的钱都应该退回去：
     * <ol>
     *   <li>预约不存在（业务都找不到了）；</li>
     *   <li>预约已作废（cancelled）；</li>
     *   <li>预约已用<b>余额</b>付清 —— 说明这笔微信单是 {@code prepay} 之后残留的重复支付。</li>
     * </ol>
     *
     * <p>第 3 条是补 {@code payAppointment} 的漏：余额支付成功后本应关掉残留的微信单，
     * 但关单可能失败（网络抖动），用户随后把那张微信单付掉，钱就收重了。
     * 「payMethod=balance 且 payTime 非空」只可能出现在余额支付成功后，判定安全。
     *
     * <p>注意：payMethod 为 wechat/alipay 时一律返回 false（含已完成的正常订单），
     * 否则会把正常收的钱退掉。
     *
     * @param order 状态为 PAID 的支付单
     * @return 这笔收款已无业务价值时返回 true
     */
    @Override
    public boolean isBizCancelled(PaymentOrder order) {
        Appointment appointment = appointmentMapper.selectById(order.getBizId());
        if (appointment == null) {
            return true;
        }
        if ("cancelled".equals(appointment.getStatus())) {
            return true;
        }
        return "balance".equals(appointment.getPayMethod()) && appointment.getPayTime() != null;
    }

    /** 从 Redis 延迟队列摘掉，失败不影响主流程（还有数据库兜底扫描） */
    private void removeFromDelayQueue(Long appointmentId) {
        try {
            resilientRedisClient.zRemove(RedisConstants.DELAY_APPOINTMENT_KEY, String.valueOf(appointmentId));
        } catch (Exception e) {
            log.error("[PayAppointment] 移除延迟队列失败，appointmentId={}", appointmentId, e);
        }
    }

    /** 拼一句人类可读的就诊描述：科室 医生 日期 时段（缺项自动省略；整体带全角括号，无内容返回空串） */
    private String describe(Appointment appointment) {
        Schedule schedule = scheduleMapper.selectById(appointment.getScheduleId());
        Doctor doctor = schedule == null ? null : doctorMapper.selectById(schedule.getDoctorId());
        Department dept = doctor == null ? null : departmentMapper.selectById(doctor.getDepartmentId());

        StringBuilder sb = new StringBuilder();
        if (dept != null && dept.getName() != null) {
            sb.append(dept.getName());
        }
        if (doctor != null && doctor.getName() != null) {
            appendPart(sb, doctor.getName());
        }
        if (schedule != null) {
            if (schedule.getScheduleDate() != null) {
                appendPart(sb, schedule.getScheduleDate().toString());
            }
            if (schedule.getTimeSlot() != null) {
                appendPart(sb, schedule.getTimeSlot());
            }
        }
        return sb.isEmpty() ? "" : "（" + sb + "）";
    }

    private void appendPart(StringBuilder sb, String part) {
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(part);
    }
}
