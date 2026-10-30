package com.system.service.impl;

import com.system.service.ScheduleCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 号源配额预热运行器：应用启动后把启用医生的余号预热到 Redis，支撑抢号时的原子扣减。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.bootstrap.redis-quota",havingValue = "true",matchIfMissing = true)
public class RedisQuotaWarmupRunner implements ApplicationRunner {

    private final ScheduleCacheService scheduleCacheService;

    /**
     * 应用启动回调：触发全部启用排班的号源配额预热。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        scheduleCacheService.warmupAllActive();
    }
}
