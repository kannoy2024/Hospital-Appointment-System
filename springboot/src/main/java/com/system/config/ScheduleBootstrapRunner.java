package com.system.config;

import com.system.service.ScheduleBootstrapService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 应用启动后检查并自动补充医生未来 7 日排班（与 sql/system.sql 默认规则一致）。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.bootstrap.schedule", havingValue = "true", matchIfMissing = true)
public class ScheduleBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleBootstrapRunner.class);

    private final ScheduleBootstrapService scheduleBootstrapService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int created = scheduleBootstrapService.ensureDefaultSchedules();
            if (created > 0) {
                log.info("已自动补充医生排班 {} 条（未来 1-7 天，每日 6 个时段）", created);
            } else {
                log.debug("医生排班检查完成，无需补充");
            }
        } catch (Exception e) {
            log.warn("自动补充医生排班失败，请检查数据库或手动在后台维护排班: {}", e.getMessage());
        }
    }
}
