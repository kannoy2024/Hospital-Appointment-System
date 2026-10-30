import request from '@/utils/request'

export function getPatientPage(params) {
  return request.get('/admin/patients', { params })
}

export function getPatient(id) {
  return request.get(`/admin/patients/${id}`)
}