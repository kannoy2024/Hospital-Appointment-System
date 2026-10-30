/** 按角色返回登录后的默认首页（与 sys_menu 首屏路径一致） */
export function getRoleHomePath(role) {
  if (role === 'ADMIN') {
    return '/admin/dashboard'
  }
  if (role === 'DOCTOR') {
    return '/admin/doctor/dashboard'
  }
  return '/login'
}

/** 管理后台路径前缀，供个人中心内链使用 */
export function getScopeBasePath(path) {
  if (path.startsWith('/admin')) {
    return '/admin'
  }
  return '/admin'
}
