package com.system.service;

import com.system.dto_vo.vo.ConsultationRecordVO;
import com.system.dto_vo.dto.ConsultationSaveRequest;
import com.system.entity.Appointment;

/**
 * 病历服务：就诊病历的保存与查询。
 */
public interface ConsultationService {

    /** 医生为一次就诊保存病历 */
    ConsultationRecordVO saveForAppointment(Appointment appointment, Long doctorId, ConsultationSaveRequest request);

    /** 根据预约 ID 查病历（医生/管理员内部使用） */
    ConsultationRecordVO getByAppointmentId(Long appointmentId);

    /** 患者端查病历（校验权限：只能看自己的） */
    ConsultationRecordVO getVisibleForPatient(Long appointmentId, Long userId);

    /** 校验病历是否完整（就诊完成前调用，不完整则抛异常） */
    void requireConsultationComplete(Long appointmentId);
}
