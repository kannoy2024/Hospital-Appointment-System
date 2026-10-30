package com.system.service;

import com.system.common.PageResult;
import com.system.dto_vo.dto.MedicineSaveRequest;
import com.system.dto_vo.vo.MedicineVO;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 药品服务：药品目录查询与管理端 CRUD。
 */
public interface MedicineService {

    /** 查询启用药品列表，支持名称关键字搜索 */
    List<MedicineVO> listActive(String keyword);

    /** 管理端分页查询药品，支持关键字搜索 */
    PageResult<MedicineVO> pageAdmin(long current, long size, String keyword);

    /** 查询单个药品详情 */
    MedicineVO getById(Long id);

    /** 管理端新增药品 */
    MedicineVO create(MedicineSaveRequest request);

    /** 管理端编辑药品 */
    MedicineVO update(MedicineSaveRequest request);

    /** 管理端删除药品 */
    void delete(Long id);
}
