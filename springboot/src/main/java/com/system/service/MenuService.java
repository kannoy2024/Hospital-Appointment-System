package com.system.service;

import com.system.dto_vo.dto.MenuSaveRequest;
import com.system.dto_vo.vo.MenuAdminVO;
import com.system.dto_vo.vo.MenuVO;

import java.util.List;

/**
 * 菜单服务：基于角色的动态菜单与后台菜单管理。
 *
 * <p>角色可见性由 sys_role_menu 中间表维护（角色编码 + 菜单 ID），
 * 一个菜单可以被多个角色同时看到，一个角色也可以看到多个菜单。
 */
public interface MenuService {

    /** 获取当前用户的菜单树（根据角色过滤） */
    List<MenuVO> listMyMenus();

    /** 管理端查询所有菜单，支持关键字搜索 */
    List<MenuAdminVO> listAllForAdmin(String keyword);

    /** 管理端新增菜单（同时绑定可见角色） */
    MenuAdminVO createMenu(MenuSaveRequest request);

    /** 管理端编辑菜单（同时全量替换可见角色） */
    MenuAdminVO updateMenu(MenuSaveRequest request);

    /** 管理端删除单个菜单 */
    void deleteMenu(Long id);

    /** 管理端批量删除菜单 */
    void deleteMenus(List<Long> ids);

    /**
     * 管理端：全量替换某个菜单的可见角色。
     *
     * @param menuId    菜单ID
     * @param roleCodes 角色编码列表（全量替换，不允许为空）
     * @return 更新后的菜单
     */
    MenuAdminVO assignRoles(Long menuId, List<String> roleCodes);
}
