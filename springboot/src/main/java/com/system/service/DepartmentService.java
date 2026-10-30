package com.system.service;

import com.system.common.PageResult;
import com.system.dto_vo.dto.DepartmentSaveRequest;
import com.system.dto_vo.vo.DepartmentVO;

import java.util.List;

/**
 * 科室服务：患者端科室查询、管理端科室 CRUD。
 */
public interface DepartmentService {

    /** 查询启用科室列表，支持名称关键字搜索 */
    List<DepartmentVO> listActive(String keyword);

    /** 查询单个科室详情 */
    DepartmentVO getById(Long id);

    /** 管理端分页查询科室，支持关键字搜索 */
    PageResult<DepartmentVO> pageAdmin(long current, long size, String keyword);

    /** 管理端新增科室 */
    DepartmentVO create(DepartmentSaveRequest request);

    /** 管理端编辑科室 */
    DepartmentVO update(DepartmentSaveRequest request);

    /** 管理端删除科室（有关联医生时禁止删除） */
    void delete(Long id);
}
