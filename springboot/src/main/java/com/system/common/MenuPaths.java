package com.system.common;

import java.util.Set;

/**
 * 与前端 vue/src/router/routeMap.js 中 /admin/* 路径保持一致。
 * 仅维护后台 /admin/* 菜单路径白名单。
 */
public final class MenuPaths {

    /** 内置后台菜单，不可删除 */
    public static final Set<String> PROTECTED = Set.of(
            "/admin/dashboard",
            "/admin/users",
            "/admin/logs",
            "/admin/menus",
            "/admin/doctor/dashboard",
            "/admin/doctor/schedules",
            "/admin/doctor/appointments",
            "/admin/patients"
    );

    /** 允许在菜单管理中配置的后台页面路径 */
    public static final Set<String> ALLOWED = Set.of(
            "/admin/dashboard",
            "/admin/users",
            "/admin/logs",
            "/admin/menus",
            "/admin/departments",
            "/admin/doctors",
            "/admin/schedules",
            "/admin/appointments",
            "/admin/medicines",
            "/admin/doctor/dashboard",
            "/admin/doctor/schedules",
            "/admin/doctor/appointments",
            "/admin/patients"
    );

    private MenuPaths() {
    }
}
