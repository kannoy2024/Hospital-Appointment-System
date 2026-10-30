import request from '@/utils/request'

export function getDepartmentPage(params) {
  return request.get('/admin/departments', { params })
}

export function getDepartmentById(id) {
  return request.get(`/admin/departments/${id}`)
}

export function createDepartment(data) {
  return request.post('/admin/departments', data)
}

export function updateDepartment(id, data) {
  return request.put(`/admin/departments/${id}`, data)
}

export function deleteDepartment(id) {
  return request.delete(`/admin/departments/${id}`)
}

export function getDepartmentList(params) {
  return request.get('/departments', { params })
}
