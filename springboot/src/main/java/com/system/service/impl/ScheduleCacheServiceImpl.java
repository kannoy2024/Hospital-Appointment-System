package com.system.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.system.common.ResilientRedisClient;
import com.system.entity.Schedule;
import com.system.mapper.ScheduleMapper;
import com.system.service.ScheduleCacheService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.system.common.RedisConstants.*;


/**
 * 排班号源缓存服务实现：基于 Redis + Lua 原子扣减号源，Redis 不可用时自动降级 MySQL 乐观锁，并提供防击穿/穿透与启动预热。
 */
@Service
@RequiredArgsConstructor
public class ScheduleCacheServiceImpl implements ScheduleCacheService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleCacheServiceImpl.class);
    
    private final DefaultRedisScript<Long> quotaDecrScript;
    private final ScheduleMapper scheduleMapper;
    private final ResilientRedisClient resilientRedisClient;

    /**
     * 降级开关：Redis 不可用时回退 MySQL 乐观锁
     */
    @Value("${app.schedule.cache-warmup:true}")
    private boolean cacheWarmup;


    /**
     * 尝试扣减号源，优先 Redis Lua 原子扣减，失败返回 false 交由调用方降级 MySQL。
     *
     * @param scheduleId 排班ID
     * @return true=扣减成功；false=号满或需降级
     */
    @Override
    public boolean tryReserveSlot(Long scheduleId) {

        String key = QUOTA_KEY_PREFIX+scheduleId;

        Long result = resilientRedisClient.executeScript(quotaDecrScript, List.of(key));

        if (result == null) {
            return false;
        }

        if (result == -1) {
            // key 不存在 → 互斥锁回填（防击穿），再试一次
            log.warn("schedule:quota:{} key not found, attempting lock fallback", scheduleId);
            int remaining = getRemainingWithLock(scheduleId);
            if (remaining <= 0) {
                return false;
            }
            result = resilientRedisClient.executeScript(quotaDecrScript, List.of(key));
            if (result == null) return false;
            return result == 1;
        }
        return result == 1;
    }

    /**
     * 释放号源，取消预约或超时未支付时把已扣减的号源加回；Redis 不可用时静默跳过。
     *
     * @param scheduleId 排班ID
     */
    @Override
    public void releaseSlot(Long scheduleId) {
        String key = QUOTA_KEY_PREFIX+scheduleId;
        resilientRedisClient.increment(key);
    }

    /**
     * 预热单个排班号源到 Redis，按 totalQuota - bookedCount 计算余量写入（最小为 0）。
     *
     * @param scheduleId 排班ID
     * @param totalQuota 总号源数
     * @param bookedCount 已预约数
     */
    @Override
    public void warmup(Long scheduleId, int totalQuota, int bookedCount) {

        int remaining = totalQuota - bookedCount;

        String key = QUOTA_KEY_PREFIX + scheduleId;

        // 8 天 + 0~4 小时随机值，防止雪崩
        long ttlSeconds = Duration.ofDays(8).getSeconds()
                + RandomUtil.randomInt(0, 14400);
        resilientRedisClient.set(key, String.valueOf(Math.max(remaining, 0)),
                Duration.ofSeconds(ttlSeconds));

        log.info("Warmed up schedule:{} remaining:{}", scheduleId, remaining);
    }

    /**
     * 批量预热前后一周的排班到 Redis，应用启动时触发；开关关闭或 Redis 不可用时跳过。
     */
    @Override
    public void warmupAllActive() {
        if (!cacheWarmup) {
            log.info("排班预热已关闭（cache-warmup=false），跳过");
            return;
        }

        if (!resilientRedisClient.isAvailable()) {
            log.warn("Redis 未启动/不可用，已自动跳过排班预热");
            return;
        }


        List<Schedule> activeList = scheduleMapper.selectList(
                new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getStatus, 1)
                        .ge(Schedule::getScheduleDate, LocalDate.now().minusDays(7))
                        .le(Schedule::getScheduleDate, LocalDate.now().plusDays(7))
        );
        for (Schedule s : activeList) {
            warmup(s.getId(), s.getTotalQuota(), s.getBookedCount());
        }
        log.info("Warmed up {} active schedules", activeList.size());
    }

    /**
     * 查询 Redis 中排班的剩余号源数。
     *
     * @param scheduleId 排班ID
     * @return 剩余号源数，-1 表示未预热或数据无效
     */
    @Override
    public int getRemaining(Long scheduleId) {
        String val = resilientRedisClient.get(QUOTA_KEY_PREFIX + scheduleId);
        if (val == null || NULL_MARKER.equals(val)) {
            return -1;                 // 未缓存 或 穿透标记
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return -1;                 // 脏数据，当未缓存处理
        }
    }

    // ==================== 缓存防护方法 ====================

    /**
     * 带互斥锁的余量查询（防击穿），缓存未命中时只允许一个请求回填数据库，其余短暂等待后重试。
     *
     * @param scheduleId 排班ID
     * @return 剩余号源数，-1 表示排班不存在
     */
    @Override
    public int getRemainingWithLock(Long scheduleId) {
        int remaining = getRemaining(scheduleId);
        if (remaining != -1) return remaining;

        String lockKey = APPOINTMENT_LOCK_QUOTA + scheduleId;
        Boolean locked = resilientRedisClient.setIfAbsent(lockKey, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));

        if (Boolean.TRUE.equals(locked)) {
            try {
                Schedule schedule = scheduleMapper.selectById(scheduleId);
                if (schedule == null) {
                    resilientRedisClient.set(QUOTA_KEY_PREFIX + scheduleId, NULL_MARKER, Duration.ofMinutes(5));
                    return -1;
                }
                warmup(scheduleId, schedule.getTotalQuota(), schedule.getBookedCount());
                return schedule.getTotalQuota() - schedule.getBookedCount();
            } finally {
                resilientRedisClient.delete(lockKey);
            }
        }

        // 没抢到锁（或 Redis 异常）→ 短暂等待后重试
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
        }
        return getRemaining(scheduleId);
    }

    /**
     * 排班不存在时向 Redis 写入空值标记（防穿透）。
     *
     * @param scheduleId 排班ID
     */
    @Override
    public void markNotFound(Long scheduleId) {
     resilientRedisClient.set(QUOTA_KEY_PREFIX + scheduleId, NULL_MARKER, Duration.ofMinutes(5));
    }

    /**
     * 删除排班的余量缓存与互斥锁缓存，排班删除时调用。
     *
     * @param scheduleId 排班ID
     */
    @Override
    public void deleteSlot(Long scheduleId) {
        resilientRedisClient.delete(QUOTA_KEY_PREFIX + scheduleId);
        resilientRedisClient.delete(APPOINTMENT_LOCK_QUOTA + scheduleId);
        log.info("Deleted schedule quota cache for schedule:{}", scheduleId);
    }
}