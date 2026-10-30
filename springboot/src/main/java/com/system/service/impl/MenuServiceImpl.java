package com.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.system.common.BusinessException;
import com.system.common.MenuPaths;
import com.system.dto_vo.dto.MenuSaveRequest;
import com.system.dto_vo.vo.MenuAdminVO;
import com.system.dto_vo.vo.MenuVO;
import com.system.entity.Menu;
import com.system.entity.RoleMenu;
import com.system.entity.User;
import com.system.mapper.MenuMapper;
import com.system.mapper.RoleMenuMapper;
import com.system.security.SecurityUtils;
import com.system.service.MenuService;
import com.system.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务实现：维护后台 /admin 菜单的查询与增删改，并维护「角色 - 菜单」可见性关系。
 *
 * <p>角色可见性存放在 sys_role_menu 中间表中（role_code + menu_id），
 * {@code sys_menu.roles} 是历史字段，已不再读写。
 *
 * <p><b>重要：菜单可见性 ≠ 接口权限。</b>隐藏菜单只是不让它出现在侧栏，
 * 真正地访问边界在各 Controller 的 {@code @PreAuthorize} 上。
 * 要给某个页面开放新角色时，必须同时调整对应接口的授权注解，
 * 否则会出现「看得见菜单、点进去 403」。
 */
@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final RoleService roleService;

    /**
     * 查询当前登录用户的角色可见后台菜单，非管理员/医生返回空列表。
     *
     * @return 菜单VO列表（每个菜单带完整可见角色列表）
     */
    @Override
    public List<MenuVO> listMyMenus() {
        User user = SecurityUtils.getCurrentUserEntity();
        String role = user.getRole();
        if (!"ADMIN".equals(role) && !"DOCTOR".equals(role)) {
            return List.of();
        }
        List<Menu> menus = roleMenuMapper.selectMenusByRole(role);
        Map<Long, List<String>> roleCodesByMenu = loadRoleCodesByMenu();
        return menus.stream()
                .map(menu -> toVO(menu, roleCodesByMenu.getOrDefault(menu.getId(), List.of())))
                .collect(Collectors.toList());
    }

    /**
     * 后台查询全部菜单，支持按名称、路径、图标模糊搜索。
     *
     * @param keyword 搜索关键字（可选）
     * @return 菜单管理VO列表，每条带可见角色
     */
    @Override
    public List<MenuAdminVO> listAllForAdmin(String keyword) {
        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<Menu>()
                .likeRight(Menu::getPath, "/admin")
                .orderByAsc(Menu::getSortOrder)
                .orderByAsc(Menu::getId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Menu::getName, keyword)
                    .or().like(Menu::getPath, keyword)
                    .or().like(Menu::getIcon, keyword));
        }
        List<Menu> menus = menuMapper.selectList(wrapper);
        Map<Long, List<String>> roleCodesByMenu = loadRoleCodesByMenu();
        return menus.stream()
                .map(menu -> toAdminVO(menu, roleCodesByMenu.getOrDefault(menu.getId(), List.of())))
                .collect(Collectors.toList());
    }

    /**
     * 新增菜单，校验通过后插入，并在同一事务内绑定可见角色。
     *
     * @param request 菜单保存请求（roleCodes 必填）
     * @return 菜单管理VO
     */
    @Override
    @Transactional
    public MenuAdminVO createMenu(MenuSaveRequest request) {
        validateMenuRequest(request, null);
        List<String> roleCodes = normalizeRoleCodes(request.getRoleCodes(), request.getPath());

        Menu menu = toEntity(request);
        menuMapper.insert(menu);
        replaceRoleMenus(menu.getId(), roleCodes);

        return toAdminVO(menuMapper.selectById(menu.getId()));
    }

    /**
     * 编辑菜单，校验存在性与参数后更新，并全量替换可见角色。
     *
     * @param request 菜单保存请求（roleCodes 必填）
     * @return 菜单管理VO
     */
    @Override
    @Transactional
    public MenuAdminVO updateMenu(MenuSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("菜单 ID 不能为空");
        }
        Menu existing = menuMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException("菜单不存在");
        }
        validateMenuRequest(request, request.getId());
        List<String> roleCodes = normalizeRoleCodes(request.getRoleCodes(), request.getPath());

        Menu menu = toEntity(request);
        menu.setId(request.getId());
        menuMapper.updateById(menu);
        replaceRoleMenus(menu.getId(), roleCodes);

        return toAdminVO(menuMapper.selectById(menu.getId()));
    }

    /**
     * 全量替换某个菜单的可见角色，用于列表页快捷调整。
     *
     * @param menuId    菜单ID
     * @param roleCodes 角色编码列表
     * @return 更新后的菜单
     */
    @Override
    @Transactional
    public MenuAdminVO assignRoles(Long menuId, List<String> roleCodes) {
        Menu existing = menuMapper.selectById(menuId);
        if (existing == null) {
            throw new BusinessException("菜单不存在");
        }
        List<String> normalized = normalizeRoleCodes(roleCodes, existing.getPath());
        replaceRoleMenus(menuId, normalized);
        return toAdminVO(menuMapper.selectById(menuId));
    }

    /**
     * 删除单个菜单，存在子菜单或属于内置菜单时拒绝删除；同时清理角色关联。
     *
     * @param id 菜单ID
     */
    @Override
    @Transactional
    public void deleteMenu(Long id) {
        Menu existing = menuMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("菜单不存在");
        }
        Long childCount = menuMapper.selectCount(
                new LambdaQueryWrapper<Menu>().eq(Menu::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("请先删除子菜单");
        }
        if (MenuPaths.PROTECTED.contains(existing.getPath())) {
            throw new BusinessException("内置菜单不可删除");
        }
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getMenuId, id));
        menuMapper.deleteById(id);
    }

    /**
     * 批量删除菜单，去重后逐个调用单删逻辑。
     *
     * @param ids 菜单ID列表
     */
    @Override
    @Transactional
    public void deleteMenus(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的菜单");
        }
        List<Long> distinct = ids.stream().distinct().toList();
        for (Long id : distinct) {
            deleteMenu(id);
        }
    }

    /**
     * 校验菜单请求：路径须为已注册的后台路径，父菜单合法且路径不重复。
     *
     * @param request 菜单保存请求
     * @param excludeId 编辑时排除的菜单ID（新增传 null）
     */
    private void validateMenuRequest(MenuSaveRequest request, Long excludeId) {
        String path = request.getPath().trim();
        if (!path.startsWith("/admin/")) {
            throw new BusinessException("菜单管理仅支持后台路径（/admin/ 开头）");
        }
        if (!MenuPaths.ALLOWED.contains(path)) {
            throw new BusinessException("路径未注册页面，请在 MenuPaths 与 vue/src/router/routeMap.js 中同步添加");
        }
        if (request.getParentId() != null && request.getParentId() > 0) {
            Menu parent = menuMapper.selectById(request.getParentId());
            if (parent == null) {
                throw new BusinessException("父菜单不存在");
            }
            if (excludeId != null && excludeId.equals(request.getParentId())) {
                throw new BusinessException("父菜单不能为自己");
            }
        }
        LambdaQueryWrapper<Menu> pathWrapper = new LambdaQueryWrapper<Menu>().eq(Menu::getPath, request.getPath());
        if (excludeId != null) {
            pathWrapper.ne(Menu::getId, excludeId);
            Menu existing = menuMapper.selectById(excludeId);
            if (existing != null && MenuPaths.PROTECTED.contains(existing.getPath())
                    && !existing.getPath().equals(path)) {
                throw new BusinessException("内置菜单不可修改路由路径");
            }
        }
        if (menuMapper.selectCount(pathWrapper) > 0) {
            throw new BusinessException("路由路径已存在");
        }
    }

    /**
     * 校验并规范化角色编码列表：去重、去空白，并逐个确认角色存在且启用。
     *
     * <p>「内置菜单不能隐藏给所有角色」这条不变量在这里兜底：
     * 虽然 DTO 上已有 {@code @NotEmpty}，但服务层仍需自证，
     * 以免以后新增的调用方绕过校验把管理员锁在门外。
     *
     * @param roleCodes 原始角色编码列表
     * @param path      菜单路径，用于给出更准确的报错文案
     * @return 去重后的角色编码列表
     */
    private List<String> normalizeRoleCodes(List<String> roleCodes, String path) {
        Set<String> distinct = new LinkedHashSet<>();
        if (roleCodes != null) {
            for (String code : roleCodes) {
                if (!StringUtils.hasText(code)) {
                    continue;
                }
                String trimmed = code.trim();
                roleService.validateRoleCode(trimmed);
                distinct.add(trimmed);
            }
        }
        if (distinct.isEmpty()) {
            if (path != null && MenuPaths.PROTECTED.contains(path)) {
                throw new BusinessException("内置菜单不能隐藏给所有角色，请至少保留一个可见角色");
            }
            throw new BusinessException("请至少选择一个角色");
        }
        return new ArrayList<>(distinct);
    }

    /**
     * 全量替换菜单的角色关联：先删后插，避免增量 diff 留下幽灵权限。
     *
     * @param menuId    菜单ID
     * @param roleCodes 角色编码列表（已校验非空）
     */
    private void replaceRoleMenus(Long menuId, List<String> roleCodes) {
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getMenuId, menuId));
        for (String roleCode : roleCodes) {
            RoleMenu relation = new RoleMenu();
            relation.setMenuId(menuId);
            relation.setRoleCode(roleCode);
            roleMenuMapper.insert(relation);
        }
    }

    /**
     * 一次性取出所有「菜单 → 角色」映射，避免列表渲染时 N+1 查询。
     *
     * @return menuId → 角色编码列表
     */
    private Map<Long, List<String>> loadRoleCodesByMenu() {
        return roleMenuMapper.selectAllPairs().stream()
                .collect(Collectors.groupingBy(RoleMenu::getMenuId,
                        Collectors.mapping(RoleMenu::getRoleCode, Collectors.toList())));
    }

    /**
     * 将保存请求转换为菜单实体，父ID与排序权重为空时取默认值。
     *
     * @param request 菜单保存请求
     * @return 菜单实体
     */
    private Menu toEntity(MenuSaveRequest request) {
        Menu menu = new Menu();
        menu.setParentId(request.getParentId() == null ? 0L : request.getParentId());
        menu.setName(request.getName().trim());
        menu.setPath(request.getPath().trim());
        menu.setIcon(StringUtils.hasText(request.getIcon()) ? request.getIcon().trim() : null);
        menu.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        return menu;
    }

    /**
     * 组装当前用户侧栏菜单 VO。
     *
     * @param menu      菜单实体
     * @param roleCodes 该菜单的可见角色
     * @return 菜单VO
     */
    private MenuVO toVO(Menu menu, List<String> roleCodes) {
        MenuVO vo = MenuVO.fromEntity(menu);
        vo.setRoleCodes(roleCodes);
        return vo;
    }

    /**
     * 组装管理端菜单 VO（单条查询角色）。
     *
     * @param menu 菜单实体
     * @return 菜单管理VO
     */
    private MenuAdminVO toAdminVO(Menu menu) {
        List<String> roleCodes = roleMenuMapper.selectList(
                        new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getMenuId, menu.getId()))
                .stream()
                .map(RoleMenu::getRoleCode)
                .collect(Collectors.toList());
        return toAdminVO(menu, roleCodes);
    }

    /**
     * 组装管理端菜单 VO。
     *
     * @param menu      菜单实体
     * @param roleCodes 该菜单的可见角色
     * @return 菜单管理VO
     */
    private MenuAdminVO toAdminVO(Menu menu, List<String> roleCodes) {
        MenuAdminVO vo = MenuAdminVO.fromEntity(menu);
        vo.setRoleCodes(roleCodes);
        return vo;
    }

}
