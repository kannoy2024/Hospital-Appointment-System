/**
 * 后台：根据 /api/menus 动态注册 /admin 子路由。
 */
import { routeMap } from './routeMap'

const ADMIN_LAYOUT = 'AdminLayout'

let dynamicReady = false

const adminHiddenChildren = [
  {
    path: "profile",
    name: "Profile",
    component: () => import("@/views/profile/Profile.vue"),
    meta: { title: "个人信息", roles: ["ADMIN", "DOCTOR"] },
  },
  {
    path: "profile/password",
    name: "ChangePassword",
    component: () => import("@/views/profile/ChangePassword.vue"),
    meta: { title: "修改密码", roles: ["ADMIN", "DOCTOR"] },
  },
  {
    path: "403",
    name: "Forbidden",
    component: () => import("@/views/error/Forbidden.vue"),
    meta: {
      title: "无权限",
      noCache: true,
      noTag: true,
      roles: ["ADMIN", "DOCTOR"],
    },
  },
];

function pathToName(path) {
  return path
    .replace(/^\//, '')
    .replace(/\//g, '_')
    .replace(/-/g, '_')
}

/**
 * 解析菜单的可见角色：
 * 新接口返回 roleCodes 数组；旧数据可能是逗号串，做一次兼容。
 * 两者都没有时兜底 ADMIN，避免脏数据导致"人人可进"。
 */
function parseMenuRoles(menu) {
  if (Array.isArray(menu.roleCodes) && menu.roleCodes.length) {
    return menu.roleCodes
  }
  if (typeof menu.roles === 'string' && menu.roles.trim()) {
    return menu.roles.split(',').map((s) => s.trim()).filter(Boolean)
  }
  return ['ADMIN']
}

function menuToRoute(menu) {
  const entry = routeMap[menu.path]
  if (!entry) {
    console.warn(`[动态路由] 未注册页面: ${menu.path}，请在 routeMap.js 添加`)
    return null
  }
  const match = menu.path.match(/^\/admin\/(.+)$/)
  if (!match) {
    return null
  }
  const roles = parseMenuRoles(menu)
  return {
    path: match[1],
    name: entry.name,
    component: entry.component,
    meta: {
      title: menu.name,
      roles,
      affix: menu.path === '/admin/dashboard' || menu.path === '/admin/doctor/dashboard',
      menuPath: menu.path
    }
  }
}

function buildAdminChildren(menus) {
  const adminMenus = menus.filter((m) => m.path?.startsWith('/admin/'))
  const fromMenus = adminMenus.map(menuToRoute).filter(Boolean)
  const paths = new Set(fromMenus.map((r) => r.path))
  const merged = [...fromMenus]
  for (const h of adminHiddenChildren) {
    if (!paths.has(h.path)) {
      merged.push(h)
    }
  }
  return merged
}

function resolveAdminRedirect(menus) {
  const adminMenus = menus.filter((m) => m.path?.startsWith('/admin/'))
  return adminMenus[0]?.path || '/admin/403'
}

export function isDynamicRoutesReady() {
  return dynamicReady
}

export function resetDynamicRoutes(router) {
  if (router.hasRoute(ADMIN_LAYOUT)) {
    router.removeRoute(ADMIN_LAYOUT)
  }
  dynamicReady = false
}

/**
 * @param {import('vue-router').Router} router
 * @param {Array} menus 来自 getMyMenus()（仅含后台菜单）
 */
export function setupDynamicRoutes(router, menus) {
  resetDynamicRoutes(router)

  const adminChildren = buildAdminChildren(menus)
  if (adminChildren.length) {
    router.addRoute({
      path: '/admin',
      name: ADMIN_LAYOUT,
      component: () => import('@/layout/AdminLayout.vue'),
      meta: { scope: 'admin' },
      redirect: resolveAdminRedirect(menus),
      children: adminChildren
    })
  }

  dynamicReady = true
}
