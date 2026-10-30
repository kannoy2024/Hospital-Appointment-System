package com.system.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.system.common.BusinessException;
import com.system.common.PageResult;
import com.system.common.ResilientRedisClient;
import com.system.dto_vo.dto.DepartmentSaveRequest;
import com.system.dto_vo.vo.DepartmentVO;
import com.system.entity.Department;
import com.system.entity.Doctor;
import com.system.mapper.DepartmentMapper;
import com.system.mapper.DoctorMapper;
import com.system.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static com.system.common.RedisConstants.*;

/**
 * 科室服务实现：负责科室的查询、增删改及缓存维护。
 */
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentServiceImpl.class);

    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final ResilientRedisClient resilientRedisClient;


    /**
     * 查询所有启用的科室列表，无关键字时走缓存。
     *
     * @param keyword 搜索关键字（可选）
     * @return 科室VO列表
     */
    @Override
    @Cacheable(
            cacheNames = CACHE_DEPARTMENT_LIST,
            key = "'all'",
            condition = "#keyword == null || #keyword.trim().isEmpty()")
    public List<DepartmentVO> listActive(String keyword) {
//        // 无关键字 → 走 Redis 缓存
//        if (!StringUtils.hasText(keyword)) {
//            // 1. 读缓存
//            List<DepartmentVO> cached = resilientRedisClient.getObject(DEPARTMENT_CACHE_PREFIX, new TypeReference<>() {});
//            if (cached != null) {
//                return cached;
//            }
//
//            // 2. 查数据库
//            // 2.1 先查全部状态为1，启用状态的科室，按照排序id排名，再按照id排名
//            LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>()
//                    .eq(Department::getStatus, 1)
//                    .orderByAsc(Department::getSortOrder)
//                    .orderByAsc(Department::getId);
//            // 2.2 但是实体类不能直接传给前端，用stream流式处理，map把每一个元素都做一次转换，对每一个元素使用一次fromEntity，最后再用collect搜集回来转换成一个List
//            List<DepartmentVO> list = departmentMapper.selectList(wrapper).stream()
//                    .map(DepartmentVO::fromEntity)
//                    .collect(Collectors.toList());
//
//            // 3. 写入缓存，TTL 2小时 + 0~5分钟随机值（防雪崩）
//            long ttl = Duration.ofHours(2).getSeconds() + RandomUtil.randomInt(0, 3000);
//            resilientRedisClient.setObject(DEPARTMENT_CACHE_PREFIX, list, Duration.ofSeconds(ttl));
//            return list;
//        }
//
//        // 有关键字 → 不走缓存（每人搜索词不一样）
//        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>()
//                .eq(Department::getStatus, 1)
//                .orderByAsc(Department::getSortOrder)
//                .orderByAsc(Department::getId);
//
//        if (StringUtils.hasText(keyword)) {
//            wrapper.like(Department::getName, keyword.trim());
//        }
//        return departmentMapper.selectList(wrapper).stream()
//                .map(DepartmentVO::fromEntity)
//                .collect(Collectors.toList());

//      尝试使用注解启动缓存
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>()
                .eq(Department::getStatus, 1)
                .orderByAsc(Department::getSortOrder)
                .orderByAsc(Department::getId);

        if (StringUtils.hasText(keyword)) {
            wrapper.like(Department::getName, keyword.trim());
        }
        return departmentMapper.selectList(wrapper).stream()
                .map(DepartmentVO::fromEntity)
                .collect(Collectors.toList());

    }
    /**
     * 根据ID查询科室详情，不存在则抛出异常。
     *
     * @param id 科室ID
     * @return 科室VO
     */
    @Override
    public DepartmentVO getById(Long id) {
        Department dept = requireDepartment(id);
        return DepartmentVO.fromEntity(dept);
    }

    /**
     * 后台分页查询科室列表，包含停用科室并支持关键字搜索。
     *
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 搜索关键字（可选）
     * @return 科室分页结果
     */
    @Override
    public PageResult<DepartmentVO> pageAdmin(long current, long size, String keyword) {
        // 1. 构建查询条件：按 sortOrder 升序，按 ID 降序（新创建的在前）
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>()
                .orderByAsc(Department::getSortOrder)
                .orderByDesc(Department::getId);

        // 2. 关键字搜索
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Department::getName, keyword.trim());
        }

        // 3. 执行分页查询（MyBatis-Plus 自动生成 COUNT 和 LIMIT 两条 SQL）
        Page<Department> page = departmentMapper.selectPage(new Page<>(current, size), wrapper);

        // 4. Entity → VO 转换
        List<DepartmentVO> records = page.getRecords().stream()
                .map(DepartmentVO::fromEntity)
                .collect(Collectors.toList());

        // 5. 封装为统一的 PageResult 返回
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    // ==================== 写操作（增删改） ====================

    /**
     * 新增科室，校验名称唯一并清除科室缓存。
     *
     * @param request 新增请求
     * @return 创建后的科室VO
     */
    @Override
    @CacheEvict(cacheNames = CACHE_DEPARTMENT_LIST, allEntries = true)
    public DepartmentVO create(DepartmentSaveRequest request) {
        // 1. 业务校验：名称唯一性（新增时排除ID为null，即不排除任何记录）
        checkNameUnique(request.getName(), null);

        // 2. DTO → Entity
        Department dept = toEntity(request);

        // 3. 插入数据库（MyBatis-Plus 自动填充 createTime / updateTime）
        departmentMapper.insert(dept);
//
//        // 4. 删除缓存
//        resilientRedisClient.delete(DEPARTMENT_CACHE_PREFIX);

        // 5. 返回完整信息（包含自动生成的ID）
        return DepartmentVO.fromEntity(dept);

    }

    /**
     * 编辑科室，校验存在性与名称唯一并刷新相关缓存。
     *
     * @param request 编辑请求
     * @return 更新后的科室VO
     */
    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = CACHE_DEPARTMENT_LIST, allEntries = true),
            @CacheEvict(cacheNames = CACHE_DOCTOR_LIST, allEntries = true) })
    public DepartmentVO update(DepartmentSaveRequest request) {

        // 1. 校验科室是否存在
        requireDepartment(request.getId());

        // 2. 校验名称唯一性（排除当前科室自身）
        checkNameUnique(request.getName(), request.getId());

        // 3. DTO → Entity
        Department dept = toEntity(request);
        dept.setId(request.getId());

        // 4. 更新数据库（自动填充 updateTime）
        departmentMapper.updateById(dept);
//
//        // 5. 删除缓存
//        resilientRedisClient.delete(DEPARTMENT_CACHE_PREFIX);
//        resilientRedisClient.delete(DOCTOR_LIST_CACHE_PREFIX+"*");

        // 6. 重新查询并返回最新数据（确保返回完整信息）
        return DepartmentVO.fromEntity(departmentMapper.selectById(request.getId()));
    }

    /**
     * 删除科室（物理删除），有关联医生时拒绝删除并清除缓存。
     *
     * @param id 科室ID
     */
    @Override
    @CacheEvict(cacheNames = CACHE_DEPARTMENT_LIST, allEntries = true)
    public void delete(Long id) {
        // 1. 校验科室是否存在
        requireDepartment(id);

        // 2. 检查是否有关联医生（包括已离职的，因为数据关联性需要维护）
        Long doctorCount = doctorMapper.selectCount(new LambdaQueryWrapper<Doctor>()
                .eq(Doctor::getDepartmentId, id));

        if (doctorCount > 0) {
            throw new BusinessException("该科室下有关联医生，无法删除");
        }

        // 3. 执行物理删除
        departmentMapper.deleteById(id);

//        // 4. 删除缓存
//        resilientRedisClient.delete(DEPARTMENT_CACHE_PREFIX);

    }

    // ==================== 私有方法：业务校验与工具方法 ====================

    /**
     * 根据ID查询科室实体，不存在则抛出异常。
     *
     * @param id 科室ID
     * @return 科室实体
     */
    private Department requireDepartment(Long id) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null) {
            throw new BusinessException("科室不存在");
        }
        return dept;
    }

    /**
     * 校验科室名称唯一性，编辑时排除自身。
     *
     * @param name 待检查的科室名称
     * @param excludeId 排除的科室ID（新增传 null）
     */
    private void checkNameUnique(String name, Long excludeId) {
        // 1. 构建查询条件：名称完全匹配
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>()
                .eq(Department::getName, name);

        // 2. 编辑时需要排除自身（否则会判断自己和自己冲突）
        if (excludeId != null) {
            wrapper.ne(Department::getId, excludeId);
        }

        // 3. 如果存在同名记录，抛出异常
        if (departmentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("科室名称已存在");
        }
    }

    /**
     * 将保存请求转换为科室实体，排序权重为空时默认0。
     *
     * @param request 前端传入的保存请求
     * @return 科室实体
     */
    private Department toEntity(DepartmentSaveRequest request) {
        Department dept = new Department();
        dept.setName(request.getName());
        dept.setDescription(request.getDescription());
        dept.setLocation(request.getLocation());

        // 排序权重为空时默认0（数字越小越靠前）
        dept.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);

        dept.setStatus(request.getStatus());
        return dept;
    }
}