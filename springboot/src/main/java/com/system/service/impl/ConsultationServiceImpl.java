package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.system.common.BusinessException;
import com.system.dto_vo.vo.ConsultationRecordVO;
import com.system.dto_vo.dto.ConsultationSaveRequest;
import com.system.entity.Appointment;
import com.system.entity.ConsultationRecord;
import com.system.mapper.AppointmentMapper;
import com.system.mapper.ConsultationRecordMapper;
import com.system.service.ConsultationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 问诊病历服务实现：负责病历的保存、查询与完成就诊前的必填校验。
 */
@Service
@RequiredArgsConstructor
public class ConsultationServiceImpl implements ConsultationService {

    private final ConsultationRecordMapper consultationRecordMapper;
    private final AppointmentMapper appointmentMapper;

    /**
     * 保存就诊病历，仅就诊中与待缴药费阶段可填写，无记录则新建、有则更新。
     *
     * @param appointment 预约单
     * @param doctorId 医生ID
     * @param request 病历保存请求
     * @return 保存后的病历
     */
    @Override
    @Transactional
    public ConsultationRecordVO saveForAppointment(Appointment appointment,
                                                   Long doctorId,
                                                   ConsultationSaveRequest request) {
        //处方状态在质询中和待支付时才能填写病历
        if (!"in_consultation".equals(appointment.getStatus())
                && !"pending_rx_pay".equals(appointment.getStatus())) {
            throw new BusinessException("当前状态不可填写病历");
        }
        //咨询记录
        ConsultationRecord record = consultationRecordMapper.selectOne(
                //确认id是否一致
                new LambdaQueryWrapper<ConsultationRecord>()
                        .eq(ConsultationRecord::getAppointmentId, appointment.getId()));
                //拼装咨询记录
        if (record == null) {
            record = new ConsultationRecord();
            record.setAppointmentId(appointment.getId());
            record.setDoctorId(doctorId);
        }
        record.setChiefComplaint(request.getChiefComplaint().trim());
        record.setPresentIllness(request.getPresentIllness());
        record.setDiagnosis(request.getDiagnosis().trim());
        record.setTreatmentPlan(request.getTreatmentPlan());
        record.setDoctorAdvice(request.getDoctorAdvice());
        if (record.getId() == null) {
            consultationRecordMapper.insert(record);
        } else {
            consultationRecordMapper.updateById(record);
        }
        return ConsultationRecordVO.fromEntity(record);
    }
    /**
     * 按预约单ID查询病历（医生端视角），不存在时返回 null。
     *
     * @param appointmentId 预约单ID
     * @return 病历（可为 null）
     */
    @Override
    public ConsultationRecordVO getByAppointmentId(Long appointmentId) {
        ConsultationRecord record = consultationRecordMapper.selectOne(
                new LambdaQueryWrapper<ConsultationRecord>()
                        .eq(ConsultationRecord::getAppointmentId, appointmentId));
        return record != null ? ConsultationRecordVO.fromEntity(record) : null;
    }
    /**
     * 查询患者可见的病历，需校验预约单归属该用户，不符合或不存在时返回 null。
     *
     * @param appointmentId 预约单ID
     * @param userId 当前用户ID
     * @return 病历（可为 null）
     */
    @Override
    public ConsultationRecordVO getVisibleForPatient(Long appointmentId, Long userId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null || !appointment.getUserId().equals(userId)) {
            return null;
        }
        ConsultationRecord record = consultationRecordMapper.selectOne(
                new LambdaQueryWrapper<ConsultationRecord>()
                        .eq(ConsultationRecord::getAppointmentId, appointmentId));
        if (record == null) {
            return null;
        }
        return ConsultationRecordVO.fromEntity(record);
    }

    /**
     * 校验病历是否完整（主诉与诊断必填），不完整时抛出业务异常。
     *
     * @param appointmentId 预约单ID
     */
    @Override
    public void requireConsultationComplete(Long appointmentId) {
        ConsultationRecord record = consultationRecordMapper.selectOne(
                new LambdaQueryWrapper<ConsultationRecord>()
                        .eq(ConsultationRecord::getAppointmentId, appointmentId));
        if (record == null
                || !StringUtils.hasText(record.getChiefComplaint())
                || !StringUtils.hasText(record.getDiagnosis())) {
            throw new BusinessException("请先完善病历（主诉与诊断必填）");
        }
    }
}
