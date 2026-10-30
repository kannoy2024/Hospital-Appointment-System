import request from '@/utils/request'

export function getMedicinePage(params) {
  return request.get('/admin/medicines', { params })
}

export function getMedicineById(id) {
  return request.get(`/admin/medicines/${id}`)
}

export function createMedicine(data) {
  return request.post('/admin/medicines', data)
}

export function updateMedicine(id, data) {
  return request.put(`/admin/medicines/${id}`, data)
}

export function deleteMedicine(id) {
  return request.delete(`/admin/medicines/${id}`)
}

export function dispensePrescription(id) {
  return request.put(`/admin/prescriptions/${id}/dispense`)
}

// 导出药品明细（Excel）：后端直接写二进制流，必须 responseType: 'blob'
export function exportMedicines(params) {
  return request.get('/admin/medicines/export', { params, responseType: 'blob' })
}
