package com.system.service;

import com.system.dto_vo.dto.PrescriptionCreateRequest;
import com.system.dto_vo.vo.PaymentPrepayVO;
import com.system.dto_vo.vo.PrescriptionVO;
import com.system.entity.Appointment;

/**
 * 处方服务：开方、查询、支付、发药。
 * 处方状态流转：unpaid → paid → dispensed。
 */
public interface PrescriptionService {

    /** 医生为一次就诊创建处方（同时预扣药品库存） */
    PrescriptionVO createForAppointment(Appointment appointment, Long doctorId, PrescriptionCreateRequest request);

    /** 根据预约 ID 查处方（医生/管理员内部使用） */
    PrescriptionVO getByAppointmentId(Long appointmentId);

    /** 患者端查处方（校验权限：只能看自己的） */
    PrescriptionVO getVisibleForPatient(Long appointmentId, Long userId);

    /** 患者支付处方药费（仅余额支付），unpaid → paid */
    PrescriptionVO payPrescription(Long prescriptionId, Long userId, String payMethod);

    /** 发起处方药费预支付（微信/支付宝），只建支付单，处方状态要等回调才变 */
    PaymentPrepayVO prepay(Long prescriptionId, Long userId, String payMethod);

    /** 药房发药，paid → dispensed，自动完成预约 */
    void dispensePrescription(Long prescriptionId);

    /** 根据处方 ID 查询 */
    PrescriptionVO getById(Long id);

    /** 校验预约能否完成（病历完整 + 处方已处理） */
    void validateCanComplete(Appointment appointment);
}
