package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.system.common.BusinessException;
import com.system.common.PageResult;
import com.system.dto_vo.dto.ConsultationSaveRequest;
import com.system.dto_vo.dto.PrescriptionCreateRequest;
import com.system.dto_vo.vo.*;
import com.system.entity.Appointment;
import com.system.entity.Schedule;
import com.system.mapper.AppointmentMapper;
import com.system.mapper.DoctorWorkspaceMapper;
import com.system.mapper.ScheduleMapper;
import com.system.security.SecurityUtils;
import com.system.service.*;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.Around;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 医生工作台门面：校验预约归属当前医生，委托预约/病历/处方服务完成诊疗操作。
 */
@Service
@RequiredArgsConstructor
public class DoctorWorkspaceServiceImpl implements DoctorWorkspaceService {

    private final DoctorService doctorService;
    private final ScheduleService scheduleService;
    private final ScheduleMapper scheduleMapper;
    private final AppointmentMapper appointmentMapper;
    private final DoctorWorkspaceMapper doctorWorkspaceMapper;
    private final AppointmentService appointmentService;
    private final ConsultationService consultationService;
    private final PrescriptionService prescriptionService;



    /**
     * 查询当前登录医生的个人资料。
     *
     * @return 医生VO
     */
    @Override
    public DoctorVO getMyProfile() {
        return doctorService.getById(requireDoctorId());
    }

    /**
     * 更新当前登录医生的个人简介。
     *
     * @param intro 个人简介
     * @return 更新后的医生VO
     */
    @Override
    public DoctorVO updateMyIntro(String intro) {
        return doctorService.updateIntro(requireDoctorId(), intro);
    }

    /**
     * 统计当前医生今日预约、待处理与已完成数量。
     *
     * @return 工作台统计数据VO
     */
    @Override
    public DoctorWorkspaceStatsVO getStats() {
        Long doctorId = requireDoctorId();
        LocalDate today = LocalDate.now();
        DoctorWorkspaceStatsVO vo = new DoctorWorkspaceStatsVO();
        vo.setTodayCount(nullToZero(doctorWorkspaceMapper.countTodayAppointments(doctorId, today)));
        vo.setPendingCount(nullToZero(doctorWorkspaceMapper.countTodayPending(doctorId, today)));
        vo.setCompletedCount(nullToZero(doctorWorkspaceMapper.countCompleted(doctorId)));
        return vo;
    }

    /**
     * null 转 0。
     *
     * @param value 可能为 null 的值
     * @return 原值（非null）或 0
     */
    private static long nullToZero(Long value) {
        return value != null ? value : 0L;
    }

    /**
     * 分页查询当前医生的排班。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param scheduleDateStart 排班日期起始（可选）
     * @param scheduleDateEnd 排班日期结束（可选）
     * @return 排班分页结果
     */
    @Override
    @Around("@annotation(logAnno)")
    public PageResult<ScheduleVO> pageMySchedules(long current,
                                                  long size,
                                                  LocalDate scheduleDateStart,
                                                  LocalDate scheduleDateEnd) {
        return scheduleService.pageAdmin(current, size, requireDoctorId(), scheduleDateStart,scheduleDateEnd);
    }

    /**
     * 分页查询当前医生名下的预约，支持按状态与排班日期过滤。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param status 预约状态（可选）
     * @param scheduleDate 排班日期（可选）
     * @return 预约分页结果
     */
    @Override
    public PageResult<AppointmentVO> pageMyAppointments(long current, long size, String status, LocalDate scheduleDate) {
        Long doctorId = requireDoctorId();
        List<Long> scheduleIds = listScheduleIds(doctorId, scheduleDate);
        if (scheduleIds.isEmpty()) {
            return new PageResult<>(List.of(), 0, current, size);
        }
        LambdaQueryWrapper<Appointment> wrapper = new LambdaQueryWrapper<Appointment>()
                .in(Appointment::getScheduleId, scheduleIds)
                .orderByDesc(Appointment::getCreateTime);
        if (StringUtils.hasText(status)) {
            if ("pending_dispense".equals(status.trim())) {
                wrapper.eq(Appointment::getStatus, "pending_rx_pay");
            } else {
                wrapper.eq(Appointment::getStatus, status.trim());
            }
        }
        Page<Appointment> page = appointmentMapper.selectPage(new Page<>(current, size), wrapper);
        List<AppointmentVO> records = page.getRecords().stream()
                .map(appointmentService::mapToVO)
                .filter(vo -> !"pending_dispense".equals(status)
                        || ("pending_rx_pay".equals(vo.getStatus()) && "paid".equals(vo.getPrescriptionStatus())))
                .filter(vo -> !"pending_rx_pay".equals(status)
                        || vo.getPrescriptionStatus() == null
                        || "unpaid".equals(vo.getPrescriptionStatus()))
                .collect(Collectors.toList());
        long total = ("pending_dispense".equals(status) || "pending_rx_pay".equals(status))
                ? records.size()
                : page.getTotal();
        return new PageResult<>(records, total, page.getCurrent(), page.getSize());
    }

    /**
     * 开始接诊，校验预约归属并将预约置为就诊中。
     *
     * @param appointmentId 预约ID
     */
    @Override
    @Transactional
    public void startAppointment(Long appointmentId) {
        Appointment appointment = requireOwnedAppointment(appointmentId);
        appointmentService.startConsultation(appointment.getId(), requireDoctorId());
    }

    /**
     * 保存预约对应的病历记录，校验预约归属。
     *
     * @param appointmentId 预约ID
     * @param request 病历保存请求
     * @return 病历记录VO
     */
    @Override
    @Transactional
    public ConsultationRecordVO saveConsultation(Long appointmentId, ConsultationSaveRequest request) {
        Appointment appointment = requireOwnedAppointment(appointmentId);
        return consultationService.saveForAppointment(appointment, requireDoctorId(), request);
    }

    /**
     * 为预约开具处方，校验预约归属。
     *
     * @param appointmentId 预约ID
     * @param request 处方创建请求
     * @return 处方VO
     */
    @Override
    @Transactional
    public PrescriptionVO createPrescription(Long appointmentId, PrescriptionCreateRequest request) {
        Appointment appointment = requireOwnedAppointment(appointmentId);
        return prescriptionService.createForAppointment(appointment, requireDoctorId(), request);
    }

    /**
     * 查询预约的完整诊疗信息，包含预约、病历与处方。
     *
     * @param appointmentId 预约ID
     * @return 诊疗信息汇总VO
     */
    @Override
    public ClinicalBundleVO getClinical(Long appointmentId) {
        Appointment appointment = requireOwnedAppointment(appointmentId);
        ClinicalBundleVO bundle = new ClinicalBundleVO();
        bundle.setAppointment(appointmentService.mapToVO(appointment));
        bundle.setConsultation(consultationService.getByAppointmentId(appointment.getId()));
        bundle.setPrescription(prescriptionService.getByAppointmentId(appointment.getId()));
        return bundle;
    }

    /**
     * 完成预约诊疗，校验预约归属后将预约置为已完成。
     *
     * @param appointmentId 预约ID
     */
    @Override
    @Transactional
    public void completeAppointment(Long appointmentId) {
        requireOwnedAppointment(appointmentId);
        appointmentService.completeClinical(appointmentId, requireDoctorId());
    }

    /**
     * 发药，校验预约归属与处方存在性后执行发药。
     *
     * @param appointmentId 预约ID
     */
    @Override
    @Transactional
    public void dispensePrescription(Long appointmentId) {
        Appointment appointment = requireOwnedAppointment(appointmentId);
        PrescriptionVO prescription = prescriptionService.getByAppointmentId(appointment.getId());
        if (prescription == null) {
            throw new BusinessException("该预约无处方");
        }
        prescriptionService.dispensePrescription(prescription.getId());
    }

    /**
     * 校验预约排班属于当前登录医生，防止越权操作。
     *
     * @param appointmentId 预约ID
     * @return 预约实体
     */
    private Appointment requireOwnedAppointment(Long appointmentId) {
        Appointment appointment = appointmentMapper.selectById(appointmentId);
        if (appointment == null) {
            throw new BusinessException("预约不存在");
        }
        Schedule schedule = scheduleMapper.selectById(appointment.getScheduleId());
        if (schedule == null || !requireDoctorId().equals(schedule.getDoctorId())) {
            throw new BusinessException(403, "无权操作该预约");
        }
        return appointment;
    }

    /**
     * 获取当前登录医生的ID，未登录或非医生时抛出异常。
     *
     * @return 医生ID
     */
    private Long requireDoctorId() {
        return SecurityUtils.requireDoctorId();
    }

    /**
     * 查询医生指定日期的排班ID列表，日期为空则查全部。
     *
     * @param doctorId 医生ID
     * @param scheduleDate 排班日期（可选）
     * @return 排班ID列表
     */
    private List<Long> listScheduleIds(Long doctorId, LocalDate scheduleDate) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, doctorId)
                .select(Schedule::getId);
        if (scheduleDate != null) {
            wrapper.eq(Schedule::getScheduleDate, scheduleDate);
        }
        List<Schedule> schedules = scheduleMapper.selectList(wrapper);
        if (schedules.isEmpty()) {
            return Collections.emptyList();
        }
        return schedules.stream().map(Schedule::getId).collect(Collectors.toList());
    }
}
