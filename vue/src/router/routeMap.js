/**
 * path → { name, component }。
 * name 必须与页面组件 defineOptions({ name }) 完全一致：
 * <keep-alive :include> 匹配的是【组件名】，不是路由 name / path。
 * /admin/*：须在 MenuPaths.java 登记，并在菜单管理或 system.sql 中配置。
 */
export const routeMap = {
  '/admin/dashboard': { name: 'AdminDashboard', component: () => import('@/views/admin/AdminDashboard.vue') },
  '/admin/users': { name: 'UserList', component: () => import('@/views/user/UserList.vue') },
  '/admin/logs': { name: 'SystemLogList', component: () => import('@/views/log/SystemLogList.vue') },
  '/admin/menus': { name: 'MenuList', component: () => import('@/views/menu/MenuList.vue') },
  '/admin/departments': { name: 'DepartmentList', component: () => import('@/views/department/DepartmentList.vue') },
  '/admin/doctors': { name: 'DoctorList', component: () => import('@/views/doctor/DoctorList.vue') },
  '/admin/schedules': { name: 'ScheduleList', component: () => import('@/views/schedule/ScheduleList.vue') },
  '/admin/appointments': { name: 'AppointmentList', component: () => import('@/views/appointment/AppointmentList.vue') },
  '/admin/medicines': { name: 'MedicineList', component: () => import('@/views/medicine/MedicineList.vue') },
  '/admin/doctor/dashboard': { name: 'DoctorDashboard', component: () => import('@/views/doctor-portal/DoctorDashboard.vue') },
  '/admin/doctor/schedules': { name: 'DoctorScheduleList', component: () => import('@/views/doctor-portal/DoctorScheduleList.vue') },
  '/admin/doctor/appointments': { name: 'DoctorAppointmentList', component: () => import('@/views/doctor-portal/DoctorAppointmentList.vue') },
  '/admin/patients': { name: 'PatientList', component: () => import('@/views/patient/PatientList.vue') },
}

export const routeMapPaths = Object.keys(routeMap)