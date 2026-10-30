package com.system.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.system.common.BusinessException;
import com.system.common.PageResult;
import com.system.common.ResilientRedisClient;
import com.system.dto_vo.dto.DoctorSaveRequest;
import com.system.dto_vo.vo.DoctorAdminVO;
import com.system.dto_vo.vo.DoctorMetricRow;
import com.system.dto_vo.vo.DoctorVO;
import com.system.entity.Department;
import com.system.entity.Doctor;
import com.system.entity.Schedule;
import com.system.mapper.DepartmentMapper;
import com.system.mapper.DoctorMapper;
import com.system.mapper.DoctorStatsMapper;
import com.system.mapper.ScheduleMapper;
import com.system.service.DoctorAccountService;
import com.system.service.DoctorService;
import com.system.service.ScheduleBootstrapService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.system.common.MessageConstants.*;
import static com.system.common.RedisConstants.DOCTOR_DETAIL_CACHE_SUFFIX;
import static com.system.common.RedisConstants.DOCTOR_LIST_CACHE_PREFIX;
import static com.system.security.SecurityUtils.getCurrentUserEntity;

/**
 * 医生服务实现：负责医生列表/详情的查询（带缓存）、后台分页管理以及医生的增删改与统计指标拼装。
 */
@Service
@RequiredArgsConstructor
public class DoctorServiceImpl implements DoctorService {

    private static final Logger log = LoggerFactory.getLogger(DoctorServiceImpl.class);

    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final ScheduleMapper scheduleMapper;
    private final DoctorStatsMapper doctorStatsMapper;
    private final DoctorAccountService doctorAccountService;
    private final ScheduleBootstrapService scheduleBootstrapService;
    private final ResilientRedisClient resilientRedisClient;

    // ==================== 前端用户端接口 ====================

    /**
     * 查询在职医生列表
     *
     * @param departmentId     科室ID，为空则查全部
     * @param keyword          关键字（匹配姓名、职称、科室名），为空则忽略
     * @param hasAvailableSlot true=仅返回有可用号源的医生
     * @return 医生 VO 列表
     */
    @Override
    public List<DoctorVO> listActive(Long departmentId, String keyword, Boolean hasAvailableSlot) {
        // 带关键字：命中率低，不缓存，直接查库
        if (StringUtils.hasText(keyword)) {
            return doListActive(departmentId, keyword, hasAvailableSlot);
        }

        // key 设计：部门 + 是否有号两个维度
        // cache:doctor:list:all:1
        // cache:doctor:list:3:0
        String cacheKey = DOCTOR_LIST_CACHE_PREFIX
                + (departmentId == null ? "all" : departmentId) + ":"
                + (Boolean.TRUE.equals(hasAvailableSlot) ? "1" : "0");

        // 1. 读缓存
        List<DoctorVO> cached = resilientRedisClient.getObject(cacheKey, new TypeReference<>() {
        });
        if (cached != null) {
            return cached;
        }

        // 2. 回源查库
        List<DoctorVO> result = doListActive(departmentId, null, hasAvailableSlot);

        // 3. 写缓存：有号源用短 TTL(60s 起)避免号源变化滞后，无号源用长 TTL(30min 起)，均加随机值防雪崩
        Duration ttl = Boolean.TRUE.equals(hasAvailableSlot)
                ? Duration.ofSeconds(180 + RandomUtil.randomInt(0, 30))
                : Duration.ofMinutes(30 + RandomUtil.randomInt(0, 5));
        resilientRedisClient.setObject(cacheKey, result, ttl);

        return result;

    }

    /**
     * 医生列表的真实查询逻辑（不含缓存），支持科室筛选、关键字搜索与仅看有号源。
     *
     * @param departmentId     科室ID，为空则查全部
     * @param keyword          关键字，为空则忽略
     * @param hasAvailableSlot true=仅返回有可用号源的医生
     * @return 医生 VO 列表
     */
    private List<DoctorVO> doListActive(Long departmentId, String keyword, Boolean hasAvailableSlot) {
        // 1. 根据是否需要号源过滤，走不同查询路径
        List<Doctor> doctors;
        if (Boolean.TRUE.equals(hasAvailableSlot)) {
            // 路径A：通过自定义 SQL 关联排班表，只查有可用号源的医生
            doctors = doctorMapper.selectActiveWithAvailableSlots(departmentId);
        } else {
            // 路径B：标准条件查询
            LambdaQueryWrapper<Doctor> wrapper = new LambdaQueryWrapper<Doctor>()
                    .eq(Doctor::getStatus, 1)
                    .orderByAsc(Doctor::getId);
            if (departmentId != null) {
                wrapper.eq(Doctor::getDepartmentId, departmentId);
            }
            if (StringUtils.hasText(keyword)) {
                applyKeywordFilter(wrapper, keyword.trim());
            }
            doctors = doctorMapper.selectList(wrapper);
        }

        // 2. 如果同时用了"有号源"和关键字，需要在内存中二次过滤
        //    （因为 SQL 中的 like 可能会匹配上科室名，但自定义 SQL 不支持动态拼接）
        if (Boolean.TRUE.equals(hasAvailableSlot) && StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase();
            Set<Long> deptIdsByName = resolveDepartmentIdsByKeyword(keyword);
            doctors = doctors.stream()
                    .filter(d -> matchesDoctorKeyword(d, kw, deptIdsByName))
                    .collect(Collectors.toList());
        }

        // 3. 批量转换为 VO（含科室名称、统计指标）
        return toVOList(doctors);
    }

    /**
     * 根据 ID 查询医生详情，优先读 Redis 缓存。
     *
     * @param id 医生ID
     * @return 医生 VO（含科室名称、就诊量、评价数等）
     */
    @Override
    public DoctorVO getById(Long id) {
        String cacheKey = DOCTOR_DETAIL_CACHE_SUFFIX + id;

        // 1. 尝试从 Redis 读取医生信息
        DoctorVO cached = resilientRedisClient.getObject(cacheKey, DoctorVO.class);
        if (cached != null) {
            return cached;
        }

        // 2. 未命中，查数据库（医生不存在时 requireDoctor 会抛 BusinessException）
        DoctorVO vo = toVO(requireDoctor(id));

        // 3. 回写缓存（TTL 30 分钟 + 0~10 分钟随机，防雪崩）
        resilientRedisClient.setObject(cacheKey, vo,
                Duration.ofMinutes(30 + RandomUtil.randomInt(0, 10)));
        return vo;
    }
    // ==================== 后端管理端接口 ====================

    /**
     * 管理后台分页查询医生列表，支持科室筛选与关键字搜索。
     *
     * @param current      当前页码
     * @param size         每页条数
     * @param departmentId 科室ID（可选）
     * @param keyword      关键字搜索（可选）
     * @return 分页结果
     */
    @Override
    public PageResult<DoctorAdminVO> pageAdmin(long current, long size, Long departmentId, String keyword) {
        // 1. 构建查询条件：按更新时间倒序
        LambdaQueryWrapper<Doctor> wrapper = new LambdaQueryWrapper<Doctor>()
                .orderByDesc(Doctor::getId);
        if (departmentId != null) {
            wrapper.eq(Doctor::getDepartmentId, departmentId);
        }
        if (StringUtils.hasText(keyword)) {
            applyKeywordFilter(wrapper, keyword.trim());
        }

        // 2. 分页查询并转 VO
        Page<Doctor> page = doctorMapper.selectPage(new Page<>(current, size), wrapper);
        return new PageResult<>(toAdminVOList(page.getRecords()), page.getTotal(), page.getCurrent(), page.getSize());
    }


    @Override
    public DoctorAdminVO getAdminById(Long id) {
        return toAdminVO(requireDoctor(id));
    }


    /**
     * 创建新医生，同步生成登录账号、初始化排班并失效相关缓存。
     *
     * @param request 创建请求（含姓名、科室、职称、性别等）
     * @return 新创建的医生 VO（含自动生成的登录用户名）
     */
    @Override
    @Transactional
    public DoctorAdminVO create(DoctorSaveRequest request) {
        // 1. 前置校验
        Department dept = requireActiveDepartment(request.getDepartmentId());
        validateGender(request.getGender());
        validateGynecologyGender(dept, request.getGender());

        // 2. 插入医生记录
        Doctor doctor = toEntity(request);
        doctorMapper.insert(doctor);

        // 3.创建登录账号 + 初始化排班
        doctorAccountService.createAccount(doctor);
        scheduleBootstrapService.ensureForDoctor(doctor.getId());

        // 4. 失效医生详情缓存 + 所有列表缓存（列表结果已变）
        resilientRedisClient.delete(DOCTOR_DETAIL_CACHE_SUFFIX + doctor.getId());
        resilientRedisClient.deleteByPattern(DOCTOR_LIST_CACHE_PREFIX + "*");

        // 5. 返回结果（携带登录用户名让前端展示给管理员）
        return toAdminVO(doctor);
    }

    /**
     * 更新医生信息，确保账号存在并同步昵称，随后失效相关缓存。
     *
     * @param request 更新请求（需包含 doctorId）
     * @return 更新后的医生 VO
     */
    @Override
    @Transactional
    public DoctorAdminVO update(DoctorSaveRequest request) {
        // 1. 前置校验
        requireDoctor(request.getId());
        Department dept = requireActiveDepartment(request.getDepartmentId());
        validateGender(request.getGender());
        validateGynecologyGender(dept, request.getGender());

        // 2. 更新医生信息
        Doctor doctor = toEntity(request);
        doctor.setId(request.getId());
        doctor.setUpdateUser(getCurrentUserEntity().getId());
        doctorMapper.updateById(doctor);

        // 3. 确保账号存在（如果之前未生成，则创建），并同步昵称
        Doctor saved = requireDoctor(request.getId());
        doctorAccountService.ensureAccount(saved);
        doctorAccountService.syncNickname(saved);

        // 4. 失效医生详情缓存 + 所有列表缓存（列表结果已变）
        resilientRedisClient.delete(DOCTOR_DETAIL_CACHE_SUFFIX + request.getId());
        resilientRedisClient.deleteByPattern(DOCTOR_LIST_CACHE_PREFIX + "*");

        return toAdminVO(saved);
    }

    /**
     * 单独更新医生简介，并失效相关缓存。
     *
     * @param id    医生ID
     * @param intro 简介内容
     * @return 更新后的医生 VO
     */
    @Override
    public DoctorVO updateIntro(Long id, String intro) {
        Doctor doctor = requireDoctor(id);
        doctor.setIntro(StringUtils.hasText(intro) ? intro.trim() : null);
        doctor.setUpdateUser(getCurrentUserEntity().getId());
        doctorMapper.updateById(doctor);
        // 失效医生详情缓存 + 所有列表缓存（简介属于列表展示字段）
        resilientRedisClient.delete(DOCTOR_DETAIL_CACHE_SUFFIX + id);
        resilientRedisClient.deleteByPattern(DOCTOR_LIST_CACHE_PREFIX + "*");
        return toVO(doctor);
    }

    /**
     * 删除医生：存在关联排班时拒绝删除，否则清理登录账号与缓存后删除。
     *
     * @param id 医生ID
     */
    @Override
    @Transactional
    public void delete(Long id) {
        // 1. 验证医生存在
        requireDoctor(id);

        // 2. 检查关联排班：有排班则禁止删除，防止数据孤岛
        Long scheduleCount = scheduleMapper.selectCount(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getDoctorId, id));
        if (scheduleCount != null && scheduleCount > 0) {
            throw new BusinessException("该医生有关联排班，无法删除");
        }
        // 3. 失效医生详情缓存 + 所有列表缓存
        resilientRedisClient.delete(DOCTOR_DETAIL_CACHE_SUFFIX + id);
        resilientRedisClient.deleteByPattern(DOCTOR_LIST_CACHE_PREFIX + "*");

        // 4. 清理账号 + 删除医生
        doctorAccountService.deleteByDoctorId(id);
        // 5. 物理删除医生
        doctorMapper.deleteById(id);
    }

    // ==================== 私有方法：校验与查询 ====================

    /**
     * 根据 ID 获取医生，不存在则抛出异常。
     *
     * @param id 医生ID
     * @return 医生实体
     */
    private Doctor requireDoctor(Long id) {
        Doctor doctor = doctorMapper.selectById(id);
        if (doctor == null) {
            throw new BusinessException("医生不存在");
        }
        return doctor;
    }

    /**
     * 校验科室存在且处于启用状态，否则抛出异常。
     *
     * @param departmentId 科室ID
     * @return 科室实体
     */
    private Department requireActiveDepartment(Long departmentId) {
        Department dept = departmentMapper.selectById(departmentId);
        if (dept == null) {
            throw new BusinessException("科室不存在");
        }
        if (dept.getStatus() != 1) {
            throw new BusinessException("科室已停用");
        }
        return dept;
    }

    /**
     * 校验性别值是否为合法的 MALE / FEMALE。
     *
     * @param gender 性别值
     */
    private void validateGender(Integer gender) {
        if (gender == null || (gender != MALE && gender != FEMALE)) {
            throw new BusinessException("性别无效");
        }
    }

    /**
     * 校验妇科科室的性别限制：妇科仅允许女医生。
     *
     * @param dept   科室实体
     * @param gender 医生性别
     */
    private void validateGynecologyGender(Department dept, Integer gender) {
        if (GYNECOLOGY_DEPT_NAME.equals(dept.getName())
                && gender != FEMALE) {
            throw new BusinessException("妇科仅允许女医生");
        }
    }

    // ==================== 私有方法：实体与 VO 转换 ====================

    /**
     * 将单个医生实体转为 VO，附加科室名称、登录用户名与统计指标。
     *
     * @param doctor 医生实体
     * @return 医生 VO
     */
    private DoctorVO toVO(Doctor doctor) {
        // 1. 查询科室名称
        Department dept = departmentMapper.selectById(doctor.getDepartmentId());
        String deptName = dept != null ? dept.getName() : "";

        // 2. 组装 VO
        DoctorVO vo = DoctorVO.fromEntity(doctor, deptName);
        applyMetrics(vo);
        return vo;
    }

    /**
     * 单个医生 → 管理端 VO（在公开 VO 基础上补登录账号）。
     */
    private DoctorAdminVO toAdminVO(Doctor doctor) {
        DoctorVO base = toVO(doctor);
        DoctorAdminVO admin = new DoctorAdminVO();
        BeanUtils.copyProperties(base, admin);
        admin.setLoginUsername(doctorAccountService.getUsernameByDoctorId(doctor.getId()));
        return admin;
    }


    /**
     * 批量医生 → 管理端 VO（科室名、统计指标、登录账号全部批量查询）。
     */
    private List<DoctorAdminVO> toAdminVOList(List<Doctor> doctors) {
        if (doctors.isEmpty()) {
            return List.of();
        }

        // 1. 批量科室名
        Set<Long> deptIds = doctors.stream().map(Doctor::getDepartmentId).collect(Collectors.toSet());
        Map<Long, String> deptNames = departmentMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));

        // 2. 批量统计指标
        Map<Long, DoctorMetricRow> metrics = loadMetrics();

        // 3. 批量登录账号（一次 IN 查询）
        Set<Long> doctorIds = doctors.stream().map(Doctor::getId).collect(Collectors.toSet());
        Map<Long, String> usernames = doctorAccountService.getUsernamesByDoctorIds(doctorIds);

        // 4. 内存组装
        return doctors.stream()
                .map(d -> {
                    DoctorVO base = DoctorVO.fromEntity(d, deptNames.getOrDefault(d.getDepartmentId(), ""));
                    applyMetrics(base, metrics.get(d.getId()));
                    DoctorAdminVO admin = new DoctorAdminVO();
                    BeanUtils.copyProperties(base, admin);
                    admin.setLoginUsername(usernames.get(d.getId()));
                    return admin;
                })
                .collect(Collectors.toList());
    }

    /**
     * 批量将医生实体转为 VO，批量查询科室名称与统计指标以避免 N+1 查询。
     *
     * @param doctors 医生实体列表
     * @return 医生 VO 列表
     */
    private List<DoctorVO> toVOList(List<Doctor> doctors) {
        if (doctors.isEmpty()) {
            return List.of();
        }

        // 1. 一次性查询所有关联科室名称
        Set<Long> deptIds = doctors.stream().map(Doctor::getDepartmentId).collect(Collectors.toSet());
        Map<Long, String> deptNames = departmentMapper.selectBatchIds(deptIds).stream()
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));

        // 2. 一次性加载所有统计指标
        Map<Long, DoctorMetricRow> metrics = loadMetrics();

        // 3. 内存中组装 VO
        return doctors.stream()
                .map(d -> {
                    DoctorVO vo = DoctorVO.fromEntity(d, deptNames.getOrDefault(d.getDepartmentId(), ""));
                    applyMetrics(vo, metrics.get(d.getId()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    // ==================== 私有方法：统计指标 ====================

    /**
     * 为单个 VO 填充统计指标（内部加载全量指标后按 ID 取值）。
     *
     * @param vo 医生 VO
     */
    private void applyMetrics(DoctorVO vo) {
        applyMetrics(vo, loadMetrics().get(vo.getId()));
    }

    /**
     * 使用已加载的指标行填充 VO 的就诊量、评价数与评分（无评价时不展示评分）。
     *
     * @param vo  医生 VO
     * @param row 统计指标行（可能为 null）
     */
    private void applyMetrics(DoctorVO vo, DoctorMetricRow row) {
        // 先设置默认值
        vo.setConsultCount(0);
        vo.setReviewCount(0);
        vo.setRating(null);

        if (row == null) {
            return;
        }

        // 填充就诊量
        if (row.getConsultCount() != null) {
            vo.setConsultCount(row.getConsultCount().intValue());
        }

        // 填充评价数和评分（只有有评价时才显示评分）
        if (row.getReviewCount() != null && row.getReviewCount() > 0) {
            vo.setReviewCount(row.getReviewCount().intValue());
            vo.setRating(row.getAvgRating());
        }
    }

    /**
     * 加载所有医生的统计指标：合并就诊量统计与评价统计，按医生 ID 汇总。
     *
     * @return 医生ID -> 统计指标行的映射
     */
    private Map<Long, DoctorMetricRow> loadMetrics() {
        Map<Long, DoctorMetricRow> map = new HashMap<>();

        // 1. 加载就诊量统计
        List<DoctorMetricRow> consults = doctorStatsMapper.selectConsultCounts();
        if (consults != null) {
            for (DoctorMetricRow row : consults) {
                if (row.getDoctorId() == null) {
                    continue;
                }
                DoctorMetricRow merged = map.computeIfAbsent(row.getDoctorId(), id -> new DoctorMetricRow());
                merged.setDoctorId(row.getDoctorId());
                merged.setConsultCount(row.getConsultCount());
            }
        }

        // 2. 加载评价统计
        List<DoctorMetricRow> reviews = doctorStatsMapper.selectReviewStats();
        if (reviews != null) {
            for (DoctorMetricRow row : reviews) {
                if (row.getDoctorId() == null) {
                    continue;
                }
                DoctorMetricRow merged = map.computeIfAbsent(row.getDoctorId(), id -> new DoctorMetricRow());
                merged.setDoctorId(row.getDoctorId());
                merged.setAvgRating(row.getAvgRating());
                merged.setReviewCount(row.getReviewCount());
            }
        }

        return map;
    }

    // ==================== 私有方法：关键字搜索 ====================

    /**
     * 将关键字条件拼接到查询包装器中，OR 匹配医生姓名、职称与科室名称。
     *
     * @param wrapper 查询包装器
     * @param kw      关键字（已 trim）
     */
    private void applyKeywordFilter(LambdaQueryWrapper<Doctor> wrapper, String kw) {
        // 先查出名称中包含关键字的科室ID列表
        Set<Long> deptIds = resolveDepartmentIdsByKeyword(kw);

        // 拼接 OR 条件：姓名 like OR 职称 like OR 科室ID in (...)
        wrapper.and(w -> {
            w.like(Doctor::getName, kw).or().like(Doctor::getTitle, kw);
            if (!deptIds.isEmpty()) {
                w.or().in(Doctor::getDepartmentId, deptIds);
            }
        });
    }

    /**
     * 根据关键字查找名称匹配的启用科室ID集合，用于关键字搜索时的科室过滤。
     *
     * @param keyword 关键字
     * @return 匹配的科室ID集合
     */
    private Set<Long> resolveDepartmentIdsByKeyword(String keyword) {
        return departmentMapper.selectList(new LambdaQueryWrapper<Department>()
                        .eq(Department::getStatus, 1)
                        .like(Department::getName, keyword))
                .stream()
                .map(Department::getId)
                .collect(Collectors.toSet());
    }

    /**
     * 在内存中判断医生是否匹配关键字：匹配姓名、职称或所属科室。
     *
     * @param doctor        医生实体
     * @param kw            小写关键字
     * @param deptIdsByName 按名称匹配的科室ID集合
     * @return true=匹配关键字
     */
    private boolean matchesDoctorKeyword(Doctor doctor, String kw, Set<Long> deptIdsByName) {
        // 匹配姓名
        if (doctor.getName() != null && doctor.getName().toLowerCase().contains(kw)) {
            return true;
        }
        // 匹配职称
        if (doctor.getTitle() != null && doctor.getTitle().toLowerCase().contains(kw)) {
            return true;
        }
        // 匹配所属科室名
        return deptIdsByName.contains(doctor.getDepartmentId());
    }

    /**
     * 将保存请求转为医生实体（不含 ID，由 Mapper 自动生成）。
     *
     * @param request 保存请求
     * @return 医生实体
     */
    private Doctor toEntity(DoctorSaveRequest request) {
        Doctor doctor = new Doctor();
        doctor.setDepartmentId(request.getDepartmentId());
        doctor.setName(request.getName());
        doctor.setTitle(request.getTitle());
        doctor.setGender(request.getGender());
        doctor.setAvatar(request.getAvatar());
        doctor.setIntro(request.getIntro());
        doctor.setStatus(request.getStatus());
        return doctor;
    }
}
