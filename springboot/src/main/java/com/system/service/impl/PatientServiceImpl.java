package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.system.common.BusinessException;
import com.system.common.PageResult;
import com.system.dto_vo.dto.PatientSaveRequest;
import com.system.dto_vo.vo.PatientVO;
import com.system.entity.Appointment;
import com.system.entity.Patient;
import com.system.entity.User;
import com.system.mapper.AppointmentMapper;
import com.system.mapper.PatientMapper;
import com.system.security.SecurityUtils;
import com.system.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;


/**
 * 就诊人服务实现：负责就诊人的增删改查、默认就诊人维护与身份证唯一性校验。
 */
@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    private final PatientMapper patientMapper;
    private final AppointmentMapper appointmentMapper;


    /**
     * 查询当前登录账号下的全部就诊人，默认就诊人优先。
     *
     * @return 就诊人VO列表
     */
    @Override
    public List<PatientVO> listMine() {
        Long userId = currentUserId();

        //查出当前登录账号下全部就诊人的列表，并排序，默认，创建时间
        LambdaQueryWrapper<Patient> query = new LambdaQueryWrapper<>();
        query.eq(Patient::getUserId, userId);
        query.orderByDesc(Patient::getIsDefault);
        query.orderByDesc(Patient::getCreateTime);

        //常规的.stream().map()使用流，将map上的每一个元素都转换一次vo。再将转换后的结果收进一个list返回
        //修改使用脱敏版本的前端展示
        return patientMapper.selectList(query).stream().map(PatientVO::fromEntityMasked).collect(Collectors.toList());
    }

    /**
     * 查询当前账号下指定就诊人。
     *
     * @param id 就诊人ID
     * @return 就诊人VO
     */
    @Override
    public PatientVO getMineById(Long id) {
        Long currentUserId = currentUserId();
        return PatientVO.fromEntity(requireOwned(id, currentUserId));
    }


    /**
     * 新增就诊人，校验身份证唯一，并按需设为默认就诊人。
     *
     * @param request 就诊人保存请求
     * @return 新增后的就诊人VO
     */
    @Override
    @Transactional
    public PatientVO create(PatientSaveRequest request) {
        Long userId = currentUserId();

        //同一个用户账号下，不允许存在同一个身份证
        checkIdCardUnique(request.getIdCard(), userId, null);

        //拼装信息
        Patient patient = new Patient();
        patient.setUserId(userId);
        applyRequest(patient, request);

        //判断是否设置了默认就诊人信息
        boolean wantDefault = isTrue(request.getIsDefault()) || countMine(userId) == 0;
        patient.setIsDefault(wantDefault ? 1 : 0);
        patientMapper.insert(patient);

        // 设为默认时，清除当前用户下其他的默认就诊人标记，确保只有一个默认就诊人
        if (wantDefault) {
            clearOtherDefault(userId, patient.getId());
        }
        // 返回vo
        return PatientVO.fromEntity(patient);
    }

    /**
     * 修改就诊人，校验归属与身份证唯一，并按需更新默认就诊人。
     *
     * @param request 就诊人保存请求（含就诊人ID）
     * @return 更新后的就诊人VO
     */
    @Override
    @Transactional
    public PatientVO update(PatientSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("就诊人id为空");
        }
        Long userId = currentUserId();
        //检查是否有权限修改就诊人信息
        Patient patient = requireOwned(request.getId(), userId);

        //检查传入的身份证唯一(排除用户本人的身份证)
        checkIdCardUnique(request.getIdCard(), userId, request.getId());

        //拼装vo
        applyRequest(patient, request);
        patientMapper.updateById(patient);

        //检查默认就诊人设置
        if (isTrue(request.getIsDefault())) {
            setDefault(patient.getId());
        }

        return PatientVO.fromEntity(patientMapper.selectById(patient.getId()));
    }


    /**
     * 删除就诊人，存在未完成预约时禁止删除，删除默认就诊人后自动补位。
     *
     * @param id 就诊人ID
     */
    @Override
    @Transactional
    public void delete(Long id) {
        Long currentUserId = currentUserId();
        Patient patient = requireOwned(id, currentUserId);

        //当就诊人还有未完成的预约时禁止删除
        Long active = appointmentMapper.selectCount(new LambdaQueryWrapper<Appointment>()
                .eq(Appointment::getPatientId, id)
                .in(Appointment::getStatus, "unpaid", "pending", "confirmed",
                        "checked_in", "in_consultation", "pending_rx_pay"));
        if (active != null && active > 0) {
            throw new BusinessException("该就诊人还存在有效排班，请勿删除");
        }

        patientMapper.deleteById(id);

        //如果删除的是默认就诊人，则需要补上一个默认就诊人，以最后添加的就诊人为准
        if (patient.getIsDefault() != null && patient.getIsDefault() == 1) {
            Patient first = patientMapper.selectOne(new LambdaQueryWrapper<Patient>()
                    .eq(Patient::getUserId, currentUserId)
                    .orderByDesc(Patient::getCreateTime)
                    .last("limit 1"));
            if (first != null) {
                patientMapper.update(null, new LambdaUpdateWrapper<Patient>()
                        .eq(Patient::getId, first.getId())
                        .set(Patient::getIsDefault, 1));
            }
        }


    }


    /**
     * 将指定就诊人设为默认，并清除该账号下其他就诊人的默认标记。
     *
     * @param id 就诊人ID
     */
    @Override
    @Transactional
    public void setDefault(Long id) {
        Long currentUserId = currentUserId();
        Patient patient = requireOwned(id, currentUserId);

        clearOtherDefault(currentUserId, patient.getId());
        patientMapper.update(null, new LambdaUpdateWrapper<Patient>()
                .eq(Patient::getId, patient.getId())
                .set(Patient::getIsDefault, 1));

    }

    /**
     * 按就诊人ID与归属用户查询就诊人。
     *
     * @param id 就诊人ID
     * @param userId 所属用户ID
     * @return 就诊人VO
     */
    @Override
    public PatientVO getOwned(Long id, Long userId) {
        Patient patient = requireOwned(id, userId);
        return PatientVO.fromEntity(patient);
    }


//管理员方法=============================================

    /**
     * 管理端分页查询就诊人：姓名、电话支持模糊搜索，身份证需完整号码精确命中。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 搜索关键词（可选）
     * @return 就诊人分页结果
     */
    @Override
    public PageResult<PatientVO> pageAdmin(long current, long size, String keyword) {
        //先按顺序查全部的就诊人信息
        LambdaQueryWrapper<Patient> wrapper = new LambdaQueryWrapper<Patient>()
                .orderByDesc(Patient::getId);

        //判断搜索关键词
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Patient::getName, kw)
                    .or().like(Patient::getPhone, kw)
                    .or().eq(Patient::getIdCard, kw.toUpperCase()));  // 只允许完整 18 位精确命中
        }
        //拼装分页返回的数据
        //修改使用脱敏信息转换
        Page<Patient> patientPage = patientMapper.selectPage(new Page<>(current, size), wrapper);
        List<PatientVO> records = patientPage
                .getRecords()
                .stream()
                .map(PatientVO::fromEntityMasked)
                .collect(Collectors.toList());

        return new PageResult<>(records, patientPage.getTotal(), patientPage.getCurrent(), patientPage.getSize());
    }

    /**
     * 按ID查询就诊人，不存在则抛出业务异常。
     *
     * @param id 就诊人ID
     * @return 就诊人VO
     */
    @Override
    public PatientVO getById(Long id) {
        Patient patient = patientMapper.selectById(id);
        if (patient == null) {
            throw new BusinessException("就诊人信息不存在");
        }
        return PatientVO.fromEntityMasked(patient);
    }

//私有方法部分==========================================================

    /**
     * 取当前登录用户 ID。
     *
     * @return 当前登录用户ID
     */
    private Long currentUserId() {
        User user = SecurityUtils.getCurrentUserEntity();
        return user.getId();
    }

    /**
     * 将保存请求中的字段拼装到就诊人实体。
     *
     * @param patient 就诊人实体
     * @param request 就诊人保存请求
     */
    private void applyRequest(Patient patient, PatientSaveRequest request) {
        patient.setName(request.getName());
        patient.setPhone(request.getPhone());
        patient.setIdCard(StringUtils.hasText(request.getIdCard()) ? request.getIdCard().trim() : null);
        patient.setGender(request.getGender());
        patient.setBirthDate(request.getBirthDate());
        patient.setNation(request.getNation());
        patient.setAddress(request.getAddress());
        patient.setMarital(request.getMarital());
    }

    /**
     * 校验同一用户账号下就诊人身份证唯一（排除 excludeId 指定的就诊人）。
     *
     * @param idCard 身份证号（为空则跳过校验）
     * @param userId 用户ID
     * @param excludeId 需排除的就诊人ID
     */
    private void checkIdCardUnique(String idCard, Long userId, Long excludeId) {
        if (!StringUtils.hasText(idCard)) {
            return;
        }
        LambdaQueryWrapper<Patient> wrapper = new LambdaQueryWrapper<Patient>()
                .eq(Patient::getUserId, userId)
                .eq(Patient::getIdCard, idCard.trim());
        if (excludeId != null) {
            wrapper.ne(Patient::getId, excludeId);
        }
        if (patientMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("该身份证已在当前账号下的就诊人中使用，请勿重复添加");
        }
    }

    /**
     * 清除该用户下除 keepId 外全部就诊人的默认标记。
     *
     * @param userId 目标用户ID
     * @param keepId 需保留默认标记的就诊人ID
     */
    private void clearOtherDefault(Long userId, Long keepId) {
        patientMapper.update(null, new LambdaUpdateWrapper<Patient>()
                .eq(Patient::getUserId, userId)
                .ne(Patient::getId, keepId)
                .set(Patient::getIsDefault, 0));
    }


    /**
     * 查询就诊人并校验其归属账号，确保仅本人可操作。
     *
     * @param id 就诊人ID
     * @param userId 用户ID
     * @return 就诊人实体
     */
    private Patient requireOwned(Long id, Long userId) {
        Patient patient = patientMapper.selectOne(new LambdaQueryWrapper<Patient>()
                .eq(Patient::getId, id)
                .eq(Patient::getUserId, userId));
        //如果没查到，那么就是不能修改
        if (patient == null) {
            throw new BusinessException(403, "就诊人不存在或无权操作");
        }
        return patient;
    }


    /**
     * 统计该用户下的就诊人数量。
     *
     * @param userId 用户ID
     * @return 就诊人数量
     */
    private long countMine(Long userId) {
        Long c = patientMapper.selectCount(new LambdaQueryWrapper<Patient>()
                .eq(Patient::getUserId, userId));
        return c == null ? 0 : c;
    }

    /**
     * 判断标记值是否为 1。
     *
     * @param flag 标记值
     * @return true=为1，false=为空或非1
     */
    private boolean isTrue(Integer flag) {
        return flag != null && flag == 1;
    }
}
