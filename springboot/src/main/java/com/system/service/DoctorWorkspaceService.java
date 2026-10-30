package com.system.service;

import com.system.common.PageResult;
import com.system.dto_vo.vo.AppointmentVO;
import com.system.dto_vo.vo.ClinicalBundleVO;
import com.system.dto_vo.vo.ConsultationRecordVO;
import com.system.dto_vo.dto.ConsultationSaveRequest;
import com.system.dto_vo.vo.DoctorVO;
import com.system.dto_vo.vo.DoctorWorkspaceStatsVO;
import com.system.dto_vo.dto.PrescriptionCreateRequest;
import com.system.dto_vo.vo.PrescriptionVO;
import com.system.dto_vo.vo.ScheduleVO;

import java.time.LocalDate;

/**
 * 医生工作台服务：医生本人视角的排班、接诊、病历、处方等操作。
 * 所有方法都基于当前登录医生的身份进行权限校验。
 */
public interface DoctorWorkspaceService {

    /** 获取当前医生自己的资料 */
    DoctorVO getMyProfile();

    /** 医生更新自己的个人简介 */
    DoctorVO updateMyIntro(String intro);

    /** 获取医生工作台统计（今日预约数、待就诊数、已完成数） */
    DoctorWorkspaceStatsVO getStats();

    /** 分页查询当前医生的排班*/
    PageResult<ScheduleVO> pageMySchedules(long current,
                                           long size,
                                           LocalDate scheduleDateStart,
                                           LocalDate scheduleDateEnd);

    /** 分页查询当前医生的预约 */
    PageResult<AppointmentVO> pageMyAppointments(long current,
                                                 long size,
                                                 String status,
                                                 LocalDate scheduleDate);

    /** 医生开始接诊（前置：患者已签到） */
    void startAppointment(Long appointmentId);

    /** 医生保存本次就诊的病历 */
    ConsultationRecordVO saveConsultation(Long appointmentId,
                                          ConsultationSaveRequest request);

    /** 医生为本次就诊开处方 */
    PrescriptionVO createPrescription(Long appointmentId,
                                      PrescriptionCreateRequest request);

    /** 获取一次就诊的完整临床数据（病历 + 处方 + 预约） */
    ClinicalBundleVO getClinical(Long appointmentId);

    /** 医生完成就诊 */
    void completeAppointment(Long appointmentId);

    /** 药房配药发药 */
    void dispensePrescription(Long appointmentId);
}
