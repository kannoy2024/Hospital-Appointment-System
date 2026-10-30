package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.system.common.BusinessException;
import com.system.common.PageResult;
import com.system.dto_vo.dto.MedicineSaveRequest;
import com.system.dto_vo.vo.MedicineVO;
import com.system.entity.Medicine;
import com.system.entity.PrescriptionItem;
import com.system.mapper.MedicineMapper;
import com.system.mapper.PrescriptionItemMapper;
import com.system.service.MedicineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;
/**
 * 药品服务实现：负责药品的增删改查、分页管理与启用药品列表查询。
 */
@Service
@RequiredArgsConstructor
public class MedicineServiceImpl implements MedicineService {

    private final MedicineMapper medicineMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;


    /**
     * 查询启用状态的药品列表，可按名称模糊筛选。
     *
     * @param keyword 名称关键字（可为空）
     * @return 药品列表
     */
    @Override
    public List<MedicineVO> listActive(String keyword) {
        LambdaQueryWrapper<Medicine> wrapper = new LambdaQueryWrapper<Medicine>()
                .eq(Medicine::getStatus, 1)
                .orderByAsc(Medicine::getName);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Medicine::getName, keyword.trim());
        }
        return medicineMapper.selectList(wrapper).stream()
                .map(MedicineVO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 管理端分页查询药品，可按名称模糊筛选。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 名称关键字（可为空）
     * @return 药品分页结果
     */
    @Override
    public PageResult<MedicineVO> pageAdmin(long current, long size, String keyword) {
        LambdaQueryWrapper<Medicine> wrapper = new LambdaQueryWrapper<Medicine>()
                .orderByDesc(Medicine::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Medicine::getName, keyword.trim());
        }
        Page<Medicine> page = medicineMapper.selectPage(new Page<>(current, size), wrapper);
        List<MedicineVO> records = page.getRecords().stream()
                .map(MedicineVO::fromEntity)
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 按ID查询药品详情，不存在时抛出业务异常。
     *
     * @param id 药品ID
     * @return 药品详情
     */
    @Override
    public MedicineVO getById(Long id) {
        return MedicineVO.fromEntity(requireMedicine(id));
    }

    /**
     * 新增一条药品记录。
     *
     * @param request 药品保存请求
     * @return 新增后的药品详情
     */
    @Override
    public MedicineVO create(MedicineSaveRequest request) {
        Medicine medicine = request.toEntity();
        medicineMapper.insert(medicine);
        return MedicineVO.fromEntity(medicine);
    }

    /**
     * 更新药品信息，药品不存在时抛出业务异常。
     *
     * @param request 药品保存请求（含ID）
     * @return 更新后的药品详情
     */
    @Override
    @Transactional
    public MedicineVO update(MedicineSaveRequest request) {
        requireMedicine(request.getId());
        Medicine medicine = request.toEntity();
        medicineMapper.updateById(medicine);
        return MedicineVO.fromEntity(requireMedicine(request.getId()));
    }

    /**
     * 删除药品，已被处方引用时抛出业务异常。
     *
     * @param id 药品ID
     */
    @Override
    public void delete(Long id) {
        requireMedicine(id);
        Long used = prescriptionItemMapper.selectCount(new LambdaQueryWrapper<PrescriptionItem>()
                .eq(PrescriptionItem::getMedicineId, id));
        if (used != null && used > 0) {
            throw new BusinessException("该药品已被处方引用，无法删除");
        }
        medicineMapper.deleteById(id);
    }

    /**
     * 按ID获取药品，不存在时抛出业务异常。
     *
     * @param id 药品ID
     * @return 药品实体
     */
    Medicine requireMedicine(Long id) {
        Medicine medicine = medicineMapper.selectById(id);
        if (medicine == null) {
            throw new BusinessException("药品不存在");
        }
        return medicine;
    }
}
