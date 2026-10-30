package com.system.service;

import com.system.common.PageResult;
import com.system.dto_vo.dto.ScheduleSaveRequest;
import com.system.dto_vo.vo.ScheduleVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班服务：患者端可预约排班查询、管理端排班 CRUD。
 * 注意：并发扣号源由 {@link ScheduleCacheService} 负责，本接口只管排班数据本身。
 */
public interface ScheduleService {

    /** 查询可预约排班，自动过滤停诊/过期/约满，支持按科室/医生/日期筛选 */
    List<ScheduleVO> listAvailable(Long departmentId, Long doctorId, LocalDate scheduleDate);

    /** 查询单个排班详情 */
    ScheduleVO getById(Long id);



    /** 管理端分页查询排班，支持按医生/日期范围筛选 */
    PageResult<ScheduleVO> pageAdmin(long current,
                                     long size,
                                     Long doctorId,
                                     LocalDate scheduleDateStart,
                                     LocalDate scheduleDateEnd);

    /** 管理端新增排班，初始号源为 0 */
    ScheduleVO create(ScheduleSaveRequest request);

    /** 管理端编辑排班（校验号源不小于已预约数） */
    ScheduleVO update(ScheduleSaveRequest request);

    /** 管理端删除排班（有有效预约时禁止删除） */
    void delete(Long id);
}
