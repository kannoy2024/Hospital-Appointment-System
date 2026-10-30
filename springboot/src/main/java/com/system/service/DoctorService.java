package com.system.service;

import com.system.common.PageResult;
import com.system.dto_vo.dto.DoctorSaveRequest;
import com.system.dto_vo.vo.DoctorAdminVO;
import com.system.dto_vo.vo.DoctorVO;

import java.util.List;

/**
 * 医生服务：患者端医生查询、管理端医生 CRUD。
 */
public interface DoctorService {

    /** 患者端查询在职医生列表，支持按科室/关键字/有号源筛选 */
    List<DoctorVO> listActive(Long departmentId, String keyword, Boolean hasAvailableSlot);

    /** 用户查询单个医生详情 */
    DoctorVO getById(Long id);

    /** 管理端分页查询医生，支持按科室/关键字筛选 */
    PageResult<DoctorAdminVO> pageAdmin(long current, long size, Long departmentId, String keyword);


    /** 管理端查询单个医生详情 */
    DoctorAdminVO getAdminById(Long id);

    /** 管理端新增医生（自动创建登录账号、初始化排班） */
    DoctorAdminVO create(DoctorSaveRequest request);

    /** 管理端编辑医生信息 */
    DoctorAdminVO update(DoctorSaveRequest request);

    /** 医生本人更新个人简介 */
    DoctorVO updateIntro(Long id, String intro);

    /** 管理端删除医生（有排班时禁止删除） */
    void delete(Long id);
}
