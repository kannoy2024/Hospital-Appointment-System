package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.system.common.BusinessException;
import com.system.dto_vo.vo.RoleVO;
import com.system.entity.Role;
import com.system.mapper.RoleMapper;
import com.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 角色服务实现：负责查询启用角色列表并校验角色编码是否有效。
 */
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;


    /**
     * 查询所有启用的角色，按排序号升序返回。
     *
     * @return 启用角色列表
     */
    @Override
    public List<RoleVO> listEnabledRoles() {
        List<Role> roles = roleMapper.selectList(
                new LambdaQueryWrapper<Role>()
                        .eq(Role::getStatus, 1)
                        .orderByAsc(Role::getSortOrder));
        return roles.stream().map(RoleVO::fromEntity).collect(Collectors.toList());
    }

    /**
     * 校验角色编码是否为空且对应启用角色存在，否则抛出业务异常。
     *
     * @param roleCode 角色编码
     */
    @Override
    public void validateRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            throw new BusinessException("角色不能为空");
        }
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<Role>()
                        .eq(Role::getCode, roleCode)
                        .eq(Role::getStatus, 1));
        if (count == null || count == 0) {
            throw new BusinessException("角色不存在或已禁用: " + roleCode);
        }
    }
}
