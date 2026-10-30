import request from '@/utils/request'

export function getSystemLogs(params) {
  return request.get('/admin/logs', { params })
}

/** DELETE /api/logs/batch */
export function deleteLogsBatch(type, ids) {
  return request.delete('/admin/logs/batch', { data: { type, ids } })
}
