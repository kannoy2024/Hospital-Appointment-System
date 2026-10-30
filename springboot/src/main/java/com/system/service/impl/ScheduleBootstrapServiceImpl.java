package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.system.entity.Doctor;
import com.system.entity.Schedule;
import com.system.mapper.DoctorMapper;
import com.system.mapper.ScheduleMapper;
import com.system.service.ScheduleBootstrapService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 排班兜底服务实现：在启动时或新增医生时补齐今日起未来 7 日排班，避免漏导种子数据导致无法预约。
 */
@Service
@RequiredArgsConstructor
public class ScheduleBootstrapServiceImpl implements ScheduleBootstrapService {

    static final List<String> DEFAULT_TIME_SLOTS = List.of(
            "08:00", "09:00", "10:00", "14:00", "15:00", "16:00"
    );
    static final int DAY_OFFSET_START = 0;
    static final int DAY_OFFSET_END = 7;
    static final int DEFAULT_QUOTA = 5;

    private final DoctorMapper doctorMapper;
    private final ScheduleMapper scheduleMapper;


    /**
     * 为所有启用医生批量补齐默认排班。
     *
     * @return 本次新建的排班条数
     */
    @Override
    public int ensureDefaultSchedules() {
        List<Doctor> doctors = doctorMapper.selectList(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getStatus, 1));
        int created = 0;
        for (Doctor doctor : doctors) {
            created += ensureForDoctor(doctor.getId());
        }
        return created;
    }

    /**
     * 为单个医生补齐今日起未来 7 日的默认排班，已存在的时段跳过。
     *
     * @param doctorId 医生ID
     * @return 本次新建的排班条数
     */
    @Override
    public int ensureForDoctor(Long doctorId) {
        if (doctorId == null) return 0;
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null || doctor.getStatus() == null || doctor.getStatus() != 1) return 0;

        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(DAY_OFFSET_END);

        // ★    优化：一次性查出该医生未来 0-7 天所有已存在的排班
        List<Schedule> existingSchedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getDoctorId, doctorId)
                        .ge(Schedule::getScheduleDate, today)
                        .le(Schedule::getScheduleDate, endDate));

        // ★ 转成 Set：key = "日期:时段"，内存 O(1) 判断
        Set<String> existingKeys = existingSchedules.stream()
                .map(s -> s.getScheduleDate() + ":" + s.getTimeSlot())
                .collect(Collectors.toSet());

        int created = 0;
        for (int offset = DAY_OFFSET_START; offset <= DAY_OFFSET_END; offset++) {
            LocalDate date = today.plusDays(offset);
            for (String slot : DEFAULT_TIME_SLOTS) {
                String key = date + ":" + slot;
                if (existingKeys.contains(key)) {   // 内存判断，不再查库
                    continue;
                }
                Schedule schedule = new Schedule();
                schedule.setDoctorId(doctorId);
                schedule.setScheduleDate(date);
                schedule.setTimeSlot(slot);
                schedule.setTotalQuota(DEFAULT_QUOTA);
                schedule.setBookedCount(0);
                schedule.setStatus(1);
                scheduleMapper.insert(schedule);
                created++;
            }
        }
        return created;
    }

}
