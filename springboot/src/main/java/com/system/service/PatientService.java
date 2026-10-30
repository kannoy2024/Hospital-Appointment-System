package com.system.service;

import com.system.common.PageResult;
import com.system.dto_vo.dto.PatientSaveRequest;
import com.system.dto_vo.vo.PatientVO;

import java.util.List;

public interface PatientService {

    /** 查询当前用户的所有就诊人（默认的排前面） */
    List<PatientVO> listMine();

    /** 查询自己的某个就诊人详情 */
    PatientVO getMineById(Long id);

    /** 新增就诊人（归属当前登录用户） */
    PatientVO create(PatientSaveRequest request);

    /** 编辑就诊人（校验归属） */
    PatientVO update(PatientSaveRequest request);

    /** 删除就诊人（校验归属） */
    void delete(Long id);

    /** 设为默认就诊人（同账号内互斥） */
    void setDefault(Long id);

    /** 供预约流程内部调用：查就诊人并校验归属，不属于自己则抛异常 */
    PatientVO getOwned(Long id, Long userId);

    PageResult<PatientVO> pageAdmin(long current, long size, String keyword);

    /** 管理员查看就诊人详情 */
    PatientVO getById(Long id);
}