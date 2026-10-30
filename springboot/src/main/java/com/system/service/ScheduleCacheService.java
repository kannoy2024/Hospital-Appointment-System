package com.system.service;

public interface ScheduleCacheService {

    /** 扣减号源 Redis + Lua 脚本，返回 true=成功，false=已满 */
    boolean tryReserveSlot(Long scheduleId);

    /** 释放号源（取消预约 / 超时自动取消时调用） */
    void releaseSlot(Long scheduleId);

    /** 预热指定排班到 Redis */
    void warmup(Long scheduleId, int totalQuota, int bookedCount);

    /** 启动时批量预热所有有效排班 */
    void warmupAllActive();

    /** 获取 Redis 中的剩余号源数，-1 表示未预热 */
    int getRemaining(Long scheduleId);

    /** 带互斥锁的余量查询，缓存未命中时回填数据库（防击穿） */
    int getRemainingWithLock(Long scheduleId);

    /** 排班不存在时缓存空值（防穿透） */
    void markNotFound(Long scheduleId);

    /** 删除排班号源缓存（排班被删除时调用，清理 Redis 残留数据） */
    void deleteSlot(Long scheduleId);
}