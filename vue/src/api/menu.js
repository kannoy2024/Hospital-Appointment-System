import request from '@/utils/request'

/** GET /api/admin/menus — 当前用户侧栏菜单（已按角色过滤） */
export function getMyMenus() {
  return request.get('/admin/menus')
}

/** GET /api/admin/menus/manage — 管理员菜单配置列表（每条带 roleCodes） */
export function getMenuManageList(params) {
  return request.get('/admin/menus/manage', { params })
}

/** POST /api/admin/menus — 新增菜单（需同时提交 roleCodes） */
export function createMenu(data) {
  return request.post('/admin/menus', data)
}

/** PUT /api/admin/menus/:id — 编辑菜单（roleCodes 为全量替换） */
export function updateMenu(id, data) {
  return request.put(`/admin/menus/${id}`, data)
}

/** PUT /api/admin/menus/:id/roles — 只改可见角色，不动菜单其他字段 */
export function updateMenuRoles(id, roleCodes) {
  return request.put(`/admin/menus/${id}/roles`, { roleCodes })
}

/** DELETE /api/admin/menus/:id */
export function deleteMenu(id) {
  return request.delete(`/admin/menus/${id}`)
}

/** DELETE /api/admin/menus/batch */
export function deleteMenus(ids) {
  return request.delete('/admin/menus/batch', { data: ids })
}
