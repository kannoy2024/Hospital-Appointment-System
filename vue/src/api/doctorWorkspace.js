import request from '@/utils/request'

export function getDoctorProfile() {
  return request.get('/doctor/me')
}

export function updateDoctorIntro(intro) {
  return request.put('/doctor/me/intro', { intro })
}

export function getDoctorStats() {
  return request.get('/doctor/stats')
}

export function getDoctorSchedules(params) {
  return request.get('/doctor/schedules', { params })
}

export function getDoctorAppointments(params) {
  return request.get('/doctor/appointments', { params })
}

export function getDoctorMedicines(params) {
  return request.get('/doctor/medicines', { params })
}

export function startDoctorAppointment(id) {
  return request.put(`/doctor/appointments/${id}/start`)
}

export function getDoctorClinical(id) {
  return request.get(`/doctor/appointments/${id}/clinical`)
}

export function saveDoctorConsultation(id, data) {
  return request.put(`/doctor/appointments/${id}/consultation`, data)
}

export function createDoctorPrescription(id, data) {
  return request.post(`/doctor/appointments/${id}/prescription`, data)
}

export function completeDoctorAppointment(id) {
  return request.put(`/doctor/appointments/${id}/complete`)
}

export function dispenseDoctorPrescription(id) {
  return request.put(`/doctor/appointments/${id}/dispense`)
}
