package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.system.common.BusinessException;
import com.system.common.PageResult;
import com.system.common.RedisConstants;
import com.system.common.ResilientRedisClient;
import com.system.dto_vo.dto.ScheduleSaveRequest;
import com.system.dto_vo.vo.ScheduleVO;
import com.system.entity.Appointment;
import com.system.entity.Department;
import com.system.entity.Doctor;
import com.system.entity.Schedule;
import com.system.mapper.AppointmentMapper;
import com.system.mapper.DepartmentMapper;
import com.system.mapper.DoctorMapper;
import com.system.mapper.ScheduleMapper;
import com.system.service.ScheduleCacheService;
import com.system.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 排班服务实现：负责医生排班的查询、新增、编辑、删除，并批量组装医生/科室/Redis 余量。
 */
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final AppointmentMapper appointmentMapper;
    private final ScheduleCacheService scheduleCacheService;
    private final ResilientRedisClient resilientRedisClient;


    //  患者端查询 

    /**
     * 查询可预约的排班列表，自动过滤停诊、过期与已约满得排班。
     *
     * @param departmentId 科室ID（可选，未指定医生时按此过滤医生）
     * @param doctorId 医生ID（可选，指定则只查该医生）
     * @param scheduleDate 指定日期（可选）
     * @return 可预约排班列表
     */
    @Override
    public List<ScheduleVO> listAvailable(Long departmentId, Long doctorId, LocalDate scheduleDate) {

        //尝试添加redis缓存，来查询可预约排班内容
        //1从redis里面查询可以预约排班的数量


        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getStatus, 1)
                .ge(Schedule::getScheduleDate, LocalDate.now())
                .apply("booked_count < total_quota")
                .orderByAsc(Schedule::getScheduleDate)
                .orderByAsc(Schedule::getTimeSlot);

        if (doctorId != null) {
            wrapper.eq(Schedule::getDoctorId, doctorId);
        } else {
            // 未指定医生 → 查出该科室下所有在职医生 ID
            List<Long> doctorIds = listActiveDoctorIds(departmentId);
            if (doctorIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(Schedule::getDoctorId, doctorIds);
        }
        if (scheduleDate != null) {
            wrapper.eq(Schedule::getScheduleDate, scheduleDate);
        }
        return toVOList(scheduleMapper.selectList(wrapper));
    }

    /**
     * 查询指定科室下所有在职医生的 ID 列表。
     *
     * @param departmentId 科室ID（为空则查全部在职医生）
     * @return 在职医生ID列表
     */
    private List<Long> listActiveDoctorIds(Long departmentId) {
        LambdaQueryWrapper<Doctor> doctorWrapper = new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getStatus, 1)
                .select(Doctor::getId);
        if (departmentId != null) {
            doctorWrapper.eq(Doctor::getDepartmentId, departmentId);
        }
        return doctorMapper.selectList(doctorWrapper)
                .stream()
                .map(Doctor::getId)
                .collect(Collectors.toList());
    }

    /**
     * 批量将排班实体转换为 VO，医生、科室与 Redis 余量均批量加载以避免 N+1 查询。
     *
     * @param schedules 排班实体列表
     * @return 排班VO列表
     */
    private List<ScheduleVO> toVOList(List<Schedule> schedules) {
        if (schedules.isEmpty()) {
            return List.of();
        }
        // 一次性批量加载医生、科室、Redis 余量
        Map<Long, Doctor> doctors = batchLoadByIds(
                schedules, Schedule::getDoctorId, Doctor::getId, doctorMapper::selectBatchIds);
        Map<Long, String> deptNames = loadDeptNames(doctors.values());
        Map<Long, Integer> remainingMap = loadRemainingMap(schedules);

        // 内存组装 VO
        return schedules.stream()
                .map(schedule -> toVO(schedule, doctors, deptNames, remainingMap))
                .collect(Collectors.toList());
    }

    //  基础查询 

    /**
     * 根据 ID 查询单个排班详情。
     *
     * @param id 排班ID
     * @return 排班VO
     */
    @Override
    public ScheduleVO getById(Long id) {
        return toVO(requireSchedule(id));
    }

    /**
     * 管理端分页查询排班，支持按医生与日期范围筛选，批量组装避免 N+1 查询。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param doctorId 医生ID（可选）
     * @param scheduleDateStart 起始日期（可选）
     * @param scheduleDateEnd 结束日期（可选）
     * @return 排班分页结果
     */
    @Override
    public PageResult<ScheduleVO> pageAdmin(long current,
                                            long size,
                                            Long doctorId,
                                            LocalDate scheduleDateStart,
                                            LocalDate scheduleDateEnd) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .orderByDesc(Schedule::getScheduleDate)
                .orderByAsc(Schedule::getTimeSlot);

        if (doctorId != null) {
            wrapper.eq(Schedule::getDoctorId, doctorId);
        }
        if (scheduleDateStart != null && scheduleDateEnd != null) {
            wrapper.between(Schedule::getScheduleDate, scheduleDateStart, scheduleDateEnd);
        } else if (scheduleDateStart != null) {
            wrapper.ge(Schedule::getScheduleDate, scheduleDateStart);
        } else if (scheduleDateEnd != null) {
            wrapper.le(Schedule::getScheduleDate, scheduleDateEnd);
        }

        // 1分页查询排班
        Page<Schedule> page = scheduleMapper.selectPage(new Page<>(current, size), wrapper);
        List<Schedule> records = page.getRecords();

        // 2与列表查询共用同一套批量组装逻辑
        return new PageResult<>(toVOList(records), page.getTotal(), page.getCurrent(), page.getSize());
    }

    //  写操作 

    /**
     * 新增排班，校验医生在职且时段不冲突，号源由入参指定。
     *
     * @param request 排班保存请求
     * @return 新增后的排班VO
     */
    @Override
    @Transactional
    public ScheduleVO create(ScheduleSaveRequest request) {
        requireActiveDoctor(request.getDoctorId());
        checkUniqueSlot(request.getDoctorId(), request.getScheduleDate(),
                request.getTimeSlot(), null);

        Schedule schedule = toEntity(request);
        schedule.setBookedCount(0);
        scheduleMapper.insert(schedule);
        return toVO(schedule);
    }

    /**
     * 编辑排班，校验医生在职、时段不冲突，且总号源不低于已预约数。
     *
     * @param request 排班保存请求（含排班ID）
     * @return 更新后的排班VO
     */
    @Override
    @Transactional
    public ScheduleVO update(ScheduleSaveRequest request) {
        Schedule existing = requireSchedule(request.getId());
        requireActiveDoctor(request.getDoctorId());
        checkUniqueSlot(request.getDoctorId(), request.getScheduleDate(),
                request.getTimeSlot(), request.getId());

        if (request.getTotalQuota() < existing.getBookedCount()) {
            throw new BusinessException("总号源不能小于已预约数");
        }

        Schedule schedule = toEntity(request);
        schedule.setId(request.getId());
        schedule.setBookedCount(existing.getBookedCount());
        scheduleMapper.updateById(schedule);
        //当排班更新的时候，一般是更新排班余号，使用更新缓存，能够避免前端出现-1的情况
        scheduleCacheService.warmup(
                request.getId(),                    // 排班 ID
                request.getTotalQuota(),            // 新的总号源
                existing.getBookedCount()           // 已预约数（不变，保留了原值）
        );

        return toVO(requireSchedule(request.getId()));
    }

    /**
     * 删除排班，存在有效预约（pending / confirmed）时不允许删除，并同步清理缓存。
     *
     * @param id 排班ID
     */
    @Override
    public void delete(Long id) {
        requireSchedule(id);

        Long activeAppointment = appointmentMapper.selectCount(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getScheduleId, id)
                        .in(Appointment::getStatus, "pending", "confirmed")
        );
        if (activeAppointment > 0) {
            throw new BusinessException("该排班存在有效预约，无法删除");
        }
        //先对排班进行数据库删除
        scheduleMapper.deleteById(id);
        //再进行缓存删除
        scheduleCacheService.deleteSlot(id);
    }

    //  私有校验方法 

    /**
     * 查询排班，不存在则抛出业务异常。
     *
     * @param id 排班ID
     * @return 排班实体
     */
    private Schedule requireSchedule(Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new BusinessException("排班不存在");
        }
        return schedule;
    }

    /**
     * 校验医生存在且处于在职状态。
     *
     * @param doctorId 医生ID
     */
    private void requireActiveDoctor(Long doctorId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null) {
            throw new BusinessException("医生不存在");
        }
        if (doctor.getStatus() != 1) {
            throw new BusinessException("医生已停用");
        }
    }

    /**
     * 校验同一医生在同一日期时段不重复排班（编辑时通过 excludeId 排除自身）。
     *
     * @param doctorId 医生ID
     * @param date 排班日期
     * @param timeSlot 时段
     * @param excludeId 需排除的排班ID（编辑场景）
     */
    private void checkUniqueSlot(Long doctorId, LocalDate date,
                                 String timeSlot, Long excludeId) {
        LambdaQueryWrapper<Schedule> wrapper = new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, doctorId)
                .eq(Schedule::getScheduleDate, date)
                .eq(Schedule::getTimeSlot, timeSlot);
        if (excludeId != null) {
            wrapper.ne(Schedule::getId, excludeId);
        }
        if (scheduleMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("该医生在该日期时段已有排班");
        }
    }

    //  实体转换 

    /**
     * 单个排班转 VO（含医生姓名、科室名与 Redis 实时余量）。
     *
     * @param schedule 排班实体
     * @return 排班VO
     */
    private ScheduleVO toVO(Schedule schedule) {
        // 只涉及一条排班时，按需单查即可，无需批量
        Doctor doctor = doctorMapper.selectById(schedule.getDoctorId());
        String doctorName = doctor != null ? doctor.getName() : "";
        String deptName = "";
        if (doctor != null) {
            Department dept = departmentMapper.selectById(doctor.getDepartmentId());
            deptName = dept != null ? dept.getName() : "";
        }

        ScheduleVO vo = ScheduleVO.fromEntity(schedule, doctorName, deptName);
        applyRemaining(vo, schedule, scheduleCacheService.getRemaining(schedule.getId()));
        return vo;
    }

    /**
     * 批量场景下的单条排班转 VO，复用已加载好的医生、科室与余量 Map。
     *
     * @param schedule 排班实体
     * @param doctors 医生ID → 医生实体
     * @param deptNames 科室ID → 科室名
     * @param remainingMap 排班ID → 剩余号源
     * @return 排班VO
     */
    private ScheduleVO toVO(Schedule schedule,
                            Map<Long, Doctor> doctors,
                            Map<Long, String> deptNames,
                            Map<Long, Integer> remainingMap) {
        Doctor doctor = doctors.get(schedule.getDoctorId());
        String doctorName = doctor != null ? doctor.getName() : "";
        String deptName = doctor != null
                ? deptNames.getOrDefault(doctor.getDepartmentId(), "")
                : "";

        ScheduleVO vo = ScheduleVO.fromEntity(schedule, doctorName, deptName);
        applyRemaining(vo, schedule, remainingMap.get(schedule.getId()));
        return vo;
    }

    /**
     * 用 Redis 实时余量覆盖 VO 中 MySQL 计算出的余量与已约数。
     *
     * @param vo 排班VO
     * @param schedule 排班实体
     * @param remaining 剩余号源；为空或为负时视为无效，保留原值
     */
    private void applyRemaining(ScheduleVO vo, Schedule schedule, Integer remaining) {
        if (remaining != null && remaining >= 0) {
            vo.setRemainQuota(remaining);
            vo.setBookedCount(schedule.getTotalQuota() - remaining);
        }
    }

    /**
     * 将排班保存请求转换为实体（不设 id 和 bookedCount，由外部设置）。
     *
     * @param request 排班保存请求
     * @return 排班实体
     */
    private Schedule toEntity(ScheduleSaveRequest request) {
        Schedule schedule = new Schedule();
        schedule.setDoctorId(request.getDoctorId());
        schedule.setScheduleDate(request.getScheduleDate());
        schedule.setTimeSlot(request.getTimeSlot());
        schedule.setTotalQuota(request.getTotalQuota());
        schedule.setStatus(request.getStatus());
        return schedule;
    }

    //  批量加载 

    /**
     * 通用批量加载：从源列表提取一批ID，用 loader 一次性查回并转成 Map。
     *
     * @param source 源数据列表
     * @param idGetter 从源元素中取ID（如 Schedule::getDoctorId）
     * @param keyGetter 从目标实体中取其ID（如 Doctor::getId）
     * @param loader 批量查询方法（如 doctorMapper::selectBatchIds）
     * @return 目标ID → 目标实体
     */
    private <T, R> Map<Long, R> batchLoadByIds(List<T> source,
                                               Function<T, Long> idGetter,
                                               Function<R, Long> keyGetter,
                                               Function<List<Long>, List<R>> loader) {
        Set<Long> ids = source.stream()
                .map(idGetter)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return loader.apply(List.copyOf(ids)).stream()
                .collect(Collectors.toMap(keyGetter, r -> r, (a, b) -> a));
    }

    /**
     * 根据医生集合批量加载科室ID到科室名的映射。
     *
     * @param doctors 医生实体集合
     * @return 科室ID → 科室名
     */
    private Map<Long, String> loadDeptNames(Iterable<Doctor> doctors) {
        Set<Long> deptIds = new HashSet<>();
        for (Doctor doctor : doctors) {
            if (doctor != null && doctor.getDepartmentId() != null) {
                deptIds.add(doctor.getDepartmentId());
            }
        }
        if (deptIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return departmentMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
    }

    /**
     * 批量从 Redis 取排班余量，与传入列表顺序一一对应，无效值自动忽略。
     *
     * @param schedules 排班列表
     * @return 排班ID → 剩余号源（仅包含有效值）
     */
    private Map<Long, Integer> loadRemainingMap(List<Schedule> schedules) {
        List<String> keys = schedules.stream()
                .map(s -> RedisConstants.QUOTA_KEY_PREFIX + s.getId())
                .toList();
        List<String> remainingValues =resilientRedisClient.multiGet(keys);

        Map<Long, Integer> remainingMap = new HashMap<>();
        if (remainingValues == null) {
            return remainingMap;
        }
        for (int i = 0; i < schedules.size(); i++) {
            String val = remainingValues.get(i);
            if (val == null) {
                continue;
            }
            try {
                remainingMap.put(schedules.get(i).getId(), Integer.parseInt(val));
            } catch (NumberFormatException ignored) {
                // 非数字值（如 NULL 标记），忽略
            }
        }
        return remainingMap;
    }
}
