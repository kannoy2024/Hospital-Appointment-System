package com.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.system.entity.Menu;
import com.system.entity.RoleMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMenuMapper extends BaseMapper<RoleMenu> {

    // RoleMenuMapper 里建议加的两个查询
    @Select("""
        SELECT m.* FROM sys_menu m
        JOIN sys_role_menu rm ON rm.menu_id = m.id
        WHERE rm.role_code = #{roleCode} AND m.path LIKE '/admin%'
        ORDER BY m.sort_order, m.id
        """)
    List<Menu> selectMenusByRole(@Param("roleCode") String roleCode);

    @Select("SELECT menu_id, role_code FROM sys_role_menu")
    List<RoleMenu> selectAllPairs();     // 管理端列表一次性取全，避免 N+1
}
