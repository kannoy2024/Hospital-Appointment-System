import request from '@/utils/request'

export function getSchedulePage(params) {
  return request.get('/admin/schedules', { params })
}

export function createSchedule(data) {
  return request.post('/admin/schedules', data)
}

export function updateSchedule(id, data) {
  return request.put(`/admin/schedules/${id}`, data)
}

export function deleteSchedule(id) {
  return request.delete(`/admin/schedules/${id}`)
}

export function exportSchedules(params) {
  return request.get('/admin/schedules/export', { params, responseType: 'blob' })
}
