import request from '@/utils/request'

export function getAppointmentPage(params) {
  return request.get('/admin/appointments', { params })
}

export function cancelAppointment(id) {
  return request.put(`/admin/appointments/${id}/cancel`)
}

export function completeAppointment(id) {
  return request.put(`/admin/appointments/${id}/complete`)
}

// 导出预约明细（Excel）：后端直接写二进制流，必须 responseType: 'blob'
export function exportAppointments(params) {
  return request.get('/admin/appointments/export', { params, responseType: 'blob' })
}
