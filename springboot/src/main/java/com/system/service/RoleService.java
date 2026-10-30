package com.system.service;

import com.system.dto_vo.vo.RoleVO;

import java.util.List;

/**
 * 角色服务：角色列表查询与角色码校验。
 */
public interface RoleService {

    /** 查询所有启用角色 */
    List<RoleVO> listEnabledRoles();

    /** 校验角色码是否合法（USER/DOCTOR/ADMIN），不合法抛异常 */
    void validateRoleCode(String roleCode);
}
