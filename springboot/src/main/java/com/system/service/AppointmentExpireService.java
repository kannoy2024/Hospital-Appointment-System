package com.system.service;

/**
 * 预约超时取消服务：扫描并取消超时未支付的预约。
 * 由定时任务触发，负责释放号源并通知用户。
 */
public interface AppointmentExpireService {

    /** 扫描并取消所有超时未支付的预约（定时任务调用） */
    void cancelExpiredUnpaid();
}
