package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.system.common.BusinessException;
import com.system.common.PinyinInitials;
import com.system.entity.Doctor;
import com.system.entity.User;
import com.system.mapper.UserMapper;
import com.system.service.DoctorAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static com.system.common.MessageConstants.USERNAME_PREFIX;

/**
 * 医生账号服务实现：负责新增医生时自动创建关联登录账号（用户名/密码/手机号/角色），并支持昵称同步与账号删除。
 */
@Service
@RequiredArgsConstructor
public class DoctorAccountServiceImpl implements DoctorAccountService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    /**
     * 默认密码（明文），存储前会用 PasswordEncoder 加密为密文。
     */
    @Value("${app.user.default-password:123}")
    String DEFAULT_PASSWORD;

    /**
     * 为医生创建登录账号，账号已存在时直接返回用户名（幂等）。
     *
     * @param doctor 医生实体（必须包含ID）
     * @return 生成的登录用户名
     */
    @Override
    public String createAccount(Doctor doctor) {
        // 1. 参数校验：医生必须已保存到数据库（有ID）
        if (doctor == null || doctor.getId() == null) {
            throw new BusinessException("医生信息无效");
        }

        // 2. 幂等性检查：如果已有账号，直接返回用户名
        User existing = findUserByDoctorId(doctor.getId());
        if (existing != null) {
            return existing.getUsername();
        }

        // 3. 生成唯一用户名（处理重名冲突）
        String username = resolveUsername(doctor.getName(), doctor.getId());

        // 4. 构建 User 对象
        User user = new User();
        user.setUsername(username);                                      // 登录名
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));     // 加密密码
        user.setNickname(doctor.getName());                             // 昵称 = 医生姓名
        user.setPhone(buildPhone(doctor.getId()));                      // 自动生成手机号
        user.setBalanceFen(0L);                                         // 初始余额 0 分（系统赠送）
        user.setStatus(1);                                              // 启用状态（1=启用，0=禁用）
        user.setRole("DOCTOR");                                         // 角色（权限控制）
        user.setDoctorId(doctor.getId());                               // 关联医生ID（外键关联）

        // 5. 插入数据库
        userMapper.insert(user);

        // 6. 返回用户名（后续可发给医生登录使用）
        return username;
    }

    /**
     * 确保账号存在，语义化别名，实际调用 createAccount。
     *
     * @param doctor 医生实体
     * @return 登录用户名
     */
    @Override
    public String ensureAccount(Doctor doctor) {
        return createAccount(doctor);
    }


    /**
     * 根据医生ID查询其登录用户名。
     *
     * @param doctorId 医生ID
     * @return 用户名，不存在返回 null
     */
    @Override
    public String getUsernameByDoctorId(Long doctorId) {
        if (doctorId == null) {
            return null;
        }
        User user = findUserByDoctorId(doctorId);
        return user != null ? user.getUsername() : null;
    }

    /**
     * 将医生姓名同步到关联账号的昵称，保持两表数据一致。
     *
     * @param doctor 医生实体（必须包含ID和Name）
     */
    @Override
    public void syncNickname(Doctor doctor) {
        // 1. 参数校验：医生ID和姓名不能为空
        if (doctor == null || doctor.getId() == null || !StringUtils.hasText(doctor.getName())) {
            return;
        }

        // 2. 查询关联的 User 账号
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getDoctorId, doctor.getId())
                .last("LIMIT 1"));  // 确保只查一条

        if (user == null) {
            return;  // 账号不存在，无需同步
        }

        // 3. 更新昵称
        user.setNickname(doctor.getName());
        userMapper.updateById(user);
    }


    /**
     * 删除医生关联的登录账号，标记为已删除，避免产生孤儿账号。
     *
     * @param doctorId 医生ID
     */
    @Override
    public void deleteByDoctorId(Long doctorId) {
        if (doctorId == null) {
            return;
        }
        // 删除所有 doctor_id = doctorId 的 User 记录
        // （理论上只有一条，因为 doctor_id 在业务上是唯一的）
        userMapper.delete(new LambdaQueryWrapper<User>()
                .eq(User::getDoctorId, doctorId));
    }


    /**
     * 批量查询医生登录用户名，一次 IN 查询，避免列表页 N+1。
     *
     * @param doctorIds 医生ID集合
     * @return key=医生ID，value=登录用户名；无账号的医生不会出现在 map 中
     */
    @Override
    public Map<Long, String> getUsernamesByDoctorIds(Collection<Long> doctorIds) {
        if (doctorIds == null || doctorIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .in(User::getDoctorId, doctorIds))
                .stream()
                .filter(u -> u.getDoctorId() != null && u.getUsername() != null)
                .collect(Collectors.toMap(User::getDoctorId, User::getUsername, (a, b) -> a));
    }


    /**
     * 生成唯一用户名：优先 doctor + 姓名拼音首字母，占用则追加医生ID。
     *
     * @param name     医生姓名
     * @param doctorId 医生ID（用于追加后缀解决重名冲突）
     * @return 唯一用户名
     */
    String resolveUsername(String name, Long doctorId) {
        // 1. 生成基础用户名：doctor + 姓名拼音首字母（小写）
        String base = USERNAME_PREFIX + PinyinInitials.from(name).toLowerCase();

        // 2. 如果基础用户名可用，直接返回
        if (isUsernameAvailable(base)) {
            return base;
        }

        // 3. 如果基础用户名被占用，追加医生ID
        String withId = base + doctorId;
        if (isUsernameAvailable(withId)) {
            return withId;
        }

        // 4. 极端情况：理论上不会发生（doctorId唯一，但万一有脏数据）
        throw new BusinessException("无法生成唯一医生账号：" + base);
    }


    /**
     * 检查用户名是否已被占用。
     *
     * @param username 待检查的用户名
     * @return true=可用，false=被占用
     */
    private boolean isUsernameAvailable(String username) {
        Long count = userMapper.countByUsernameIgnoreDeleted(username);
        return count == null || count == 0;
    }

    /**
     * 基于医生ID生成默认占位手机号，格式为 138 + 8位医生ID。
     *
     * @param doctorId 医生ID
     * @return 11位手机号
     */
    private String buildPhone(Long doctorId) {
        // String.format("138%08d", doctorId)  →  138 + 8位数字（不足补零）
        // .substring(0, 11)                  →  截取前11位（保证长度正确）
        return String.format("138%08d", doctorId).substring(0, 11);
    }

    /**
     * 根据医生ID查询关联的 User 账号。
     *
     * @param doctorId 医生ID
     * @return User实体，不存在返回 null
     */
    private User findUserByDoctorId(Long doctorId) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getDoctorId, doctorId)
                .last("LIMIT 1"));  // 防御性查询，防止多条记录
    }
}