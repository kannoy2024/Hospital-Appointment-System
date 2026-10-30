import request from '@/utils/request'

export function getDoctorPage(params) {
  return request.get('/admin/doctors', { params })
}

export function createDoctor(data) {
  return request.post('/admin/doctors', data)
}

export function updateDoctor(id, data) {
  return request.put(`/admin/doctors/${id}`, data)
}

export function deleteDoctor(id) {
  return request.delete(`/admin/doctors/${id}`)
}

export function getDoctorList(params) {
  return request.get('/doctors', { params })
}
