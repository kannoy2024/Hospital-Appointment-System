package com.system.service;

public interface ScheduleBootstrapService {

    /**
     * 为所有启用医生补齐未来默认排班（与 system.sql 种子规则一致）。
     *
     * @return 本次新增的排班条数
     */
    int ensureDefaultSchedules();

    /**
     * 为单个医生补齐默认排班。
     */
    int ensureForDoctor(Long doctorId);
}
