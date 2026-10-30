package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.system.common.RedisConstants;
import com.system.common.ResilientRedisClient;
import com.system.entity.Appointment;
import com.system.entity.PaymentOrder;
import com.system.mapper.AppointmentMapper;
import com.system.mapper.ScheduleMapper;
import com.system.service.AppointmentExpireService;
import com.system.service.NotificationService;
import com.system.service.payment.PaymentService;
import com.system.service.ScheduleCacheService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 预约超时服务实现：负责扫描 Redis 延迟队列取消超时未支付预约，并释放号源、通知用户。
 */
@Service
@RequiredArgsConstructor
public class AppointmentExpireServiceImpl implements AppointmentExpireService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentExpireServiceImpl.class);

    private final AppointmentMapper appointmentMapper;
    private final ScheduleMapper scheduleMapper;
    private final NotificationService notificationService;
    private final ScheduleCacheService scheduleCacheService;
    private final ResilientRedisClient resilientRedisClient;
    private final PaymentService paymentService;

    // 超时阈值（分钟），从配置文件读取，默认30分钟
    @Value("${app.appointment.unpaid-expire-minutes:30}")
    private int unpaidExpireMinutes;

    private volatile long lastFallbackScan = 0;
    private static final long FALLBACK_SCAN_INTERVAL_MS = 60_000;   // 兜底扫描最多每分钟一次

    /**
     * 定时任务：扫描 Redis 延迟队列，取消超时未支付预约。
     * 每 5 秒执行一次，从 ZSet 取出 score 已到期的预约 ID，再查库做二次校验；Redis 不可用时走数据库兜底扫描（每分钟最多一次）。
     */
    @Override
    @Scheduled(fixedDelayString = "${app.appointment.expire-check-ms:5000}")  // 默认5秒钟
    public void cancelExpiredUnpaid() {
        //拼装Redis
        String key = RedisConstants.DELAY_APPOINTMENT_KEY;
        long now = System.currentTimeMillis();

        //想借助redis装饰类检查redis状态
        if (!resilientRedisClient.isAvailable()) {
            long nowTs = System.currentTimeMillis();
            if(nowTs-lastFallbackScan>FALLBACK_SCAN_INTERVAL_MS){
                lastFallbackScan = now;
                log.warn("Redis 不可用，执行数据库兜底扫描");
                fallbackScanByDatabase();
            }
            return;
        }

        //取出全部已经到期预约的id
        Set<String> expiredIds = resilientRedisClient.zRangeByScore(key, 0, now);

        if(expiredIds == null || expiredIds.isEmpty()){
            return ;
        }

        for (String id : expiredIds) {
            long appointmentId = Long.parseLong(id);//取出预约id
            try {
                cancelIfUnpaid(appointmentId);
            } catch (Exception e) {
                log.error("取消预约失败, appointmentId:{}", appointmentId, e);
            }finally {
               resilientRedisClient.zRemove(key,id);
            }
        }
        log.info("延迟队列扫描完成，处理 {} 个到期预约", expiredIds.size());
    }

    /**
     * 二次校验并取消预约：仅当预约仍为 unpaid 时才真正取消，改状态、释放号源并通知用户。
     *
     * @param appointmentId 预约ID
     */
    private void cancelIfUnpaid(Long appointmentId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        //双重确认
        if (appointment == null) {
            log.warn("预约不存在，可能已经被删除:{}",appointmentId);
            return;
        }
        //只有unpaid才能取消
        if(!"unpaid".equals(appointment.getStatus())){
            log.info("预约{}已经不是待支付状态（{}）,跳过取消",appointmentId,appointment.getStatus());
            return;
        }

        // ★先关支付单：用户如果点开过收银台，库里还有一张 CREATED 支付单，
        //   不先关掉就会留下「号已释放、支付单还活着」的窗口（用户之后付款成功 = 资损）。
        //   关单成功后，取消预约 + 回补号源 + 通知用户由 AppointmentPayHandler.onPayClosed 完成，
        //   这里直接返回，绝不能再做第二遍（否则号源会被回补两次）。
        String outTradeNo = paymentService.findCreatedOutTradeNo(PaymentOrder.BIZ_APPOINTMENT, appointmentId);
        if (outTradeNo != null && paymentService.closeIfCreated(outTradeNo)) {
            log.info("预约{}的支付单{}已关闭，取消动作由 AppointmentPayHandler 完成", appointmentId, outTradeNo);
            return;
        }

        // 真正取消：改状态 + 释放号源 + 通知用户
        appointment.setStatus("cancelled");
        appointmentMapper.updateById(appointment);
        scheduleMapper.decrementBookedCount(appointment.getScheduleId());
        scheduleCacheService.releaseSlot(appointment.getScheduleId());

        //通知用户
        notificationService.notifyUser(
                appointment.getUserId(),
                "appointment_expired",
                "预约已超时",
                "您的预约超过 " + unpaidExpireMinutes + " 分钟未支付，已自动取消并释放号源。",
                "appointment",
                appointment.getId()
        );

        //操作日志
        log.info("预约{}已超时自动取消",appointmentId);

    }

    /**
     * 兜底扫描：Redis 不可用时，直接从数据库查出超时未支付的预约并逐个取消。
     */
    private void fallbackScanByDatabase() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(unpaidExpireMinutes);
        List<Appointment> expired = appointmentMapper.selectList(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getStatus, "unpaid")
                        .lt(Appointment::getCreateTime, deadline));
        log.info("数据库兜底扫描完成，处理 {} 个到期预约", expired.size());
        for (Appointment a : expired) {
            cancelIfUnpaid(a.getId());
        }
    }
}