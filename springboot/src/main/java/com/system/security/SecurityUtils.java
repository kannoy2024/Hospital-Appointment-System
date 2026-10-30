package com.system.security;

import com.system.common.BusinessException;
import com.system.entity.User;
import lombok.NoArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spring Security工具类
 * <p>
 * 设计理念：
 * 1. 封装SecurityContextHolder的复杂操作，提供简洁API
 * 2. 统一异常处理（未登录/权限不足都转为BusinessException）
 * 3. 类型安全的用户信息获取（返回LoginUser而非Object）
 * 4. 单一职责：只负责获取认证信息，不包含业务逻辑
 * <p>
 * 使用场景：
 * - Controller中获取当前用户信息
 * - Service中验证用户权限
 * - 切面/拦截器中记录操作人
 *
 * @author System
 */
@NoArgsConstructor
public final class SecurityUtils {

    /**
     * 获取当前登录用户（LoginUser对象）
     * <p>
     * LoginUser是User的包装类，实现了UserDetails接口，
     * 包含用户信息和权限信息
     *
     * @return LoginUser 登录用户对象
     * @throws BusinessException 401 - 未登录
     */
    public static LoginUser getCurrentUser() {
        // 1. 从SecurityContext获取认证对象
        // SecurityContextHolder使用ThreadLocal存储，线程安全
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 2. 验证认证对象是否存在且为正确类型
        // instanceof模式匹配（Java 16+特性）
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            // 抛出业务异常（统一异常处理机制）
            throw new BusinessException(401, "未登录");
        }
        return loginUser;
    }

    /**
     * 获取当前登录用户的实体对象
     * <p>
     * 大多数业务场景只需要User实体，不需要权限信息
     * 这个方法提供了更简洁的访问方式
     *
     * @return User 用户实体
     */
    public static User getCurrentUserEntity() {
        return getCurrentUser().getUser();
    }

    /**
     * 判断当前用户是否为管理员
     *
     * @return true=管理员，false=非管理员
     */
    public static boolean isAdmin() {
        return "ADMIN".equals(getCurrentUserEntity().getRole());
    }

    /**
     * 判断当前用户是否为医生
     *
     * @return true=医生，false=非医生
     */
    public static boolean isDoctor() {
        return "DOCTOR".equals(getCurrentUserEntity().getRole());
    }

    /**
     * 获取当前用户的医生ID（带权限验证）
     * <p>
     * 业务场景：
     * - 医生查看自己的患者列表
     * - 医生编辑自己的排班信息
     * - 医生处理自己的预约订单
     * <p>
     * 安全检查：
     * 1. 必须是医生角色
     * 2. 医生必须已绑定档案（doctorId不为空）
     *
     * @return Long 医生ID（关联doctor表的主键）
     * @throws BusinessException 403 - 非医生账号
     * @throws BusinessException 400 - 医生账号未绑定档案
     */
    public static Long requireDoctorId() {
        // 1. 获取当前用户
        User user = getCurrentUserEntity();

        // 2. 验证角色
        if (!"DOCTOR".equals(user.getRole())) {
            throw new BusinessException(403, "非医生账号");
        }

        // 3. 验证医生档案
        if (user.getDoctorId() == null) {
            throw new BusinessException("医生账号未绑定档案，请联系管理员");
        }

        return user.getDoctorId();
    }
}