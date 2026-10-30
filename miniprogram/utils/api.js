/**
 * 业务 API
 */
const { request } = require('./request')

// 获取科室列表（支持查询参数）
function getDepartments(params = {}) {
  return request({ url: '/api/departments', data: params })
}

// 获取医生列表（支持科室、关键词等查询参数）
function getDoctors(params = {}) {
  return request({ url: '/api/doctors', data: params })
}

// 按 ID 获取医生详情
function getDoctorById(id) {
  return request({ url: `/api/doctors/${id}` })
}

// 获取医生排班号源列表
function getSchedules(params = {}) {
  return request({ url: '/api/schedules', data: params })
}

// 获取预约订单列表（status 为 all 或空时表示不筛选状态）
function getAppointments(params = {}) {
  const query = { ...params }
  if (!query.status || query.status === 'all') {
    delete query.status
  }
  return request({ url: '/api/appointments', data: query })
}

// 按 ID 获取预约订单详情
function getAppointmentById(id) {
  return request({ url: `/api/appointments/${id}` })
}

// 创建预约挂号订单
function createAppointment(data) {
  return request({ url: '/api/appointments', method: 'POST', data })
}

// 支付指定的挂号订单（仅余额通道；微信/支付宝必须走 prepayAppointment）
function payAppointment(id, payMethod) {
  return request({ url: `/api/appointments/${id}/pay`, method: 'POST', data: { payMethod } })
}

// 预支付：为挂号订单创建支付单，返回支付参数。
// ⚠️ 这里拿不到「支付结果」—— 后端只建 payment_order（status=CREATED），
// 预约仍是 unpaid；真正把预约推进到 pending 的只有网关回调。
// 支付结果必须靠 getPaymentOrder 轮询。
function prepayAppointment(id, payMethod) {
  return request({ url: `/api/appointments/${id}/prepay`, method: 'POST', data: { payMethod } })
}

// 查询支付单（轮询用）。status 取值见后端 PaymentOrder：
// CREATED / PAID / REFUNDING / REFUNDED / CLOSED / FAILED
// 注意：单不存在或越权时后端返回 Result.fail，request 会 reject，调用方必须 catch。
function getPaymentOrder(outTradeNo) {
  return request({ url: `/api/payments/${outTradeNo}` })
}

// 查询退款单（取消已支付预约后看退款进度用）。status 取值见后端 RefundOrder：
// PROCESSING / SUCCESS / FAILED / CLOSED
// ★ 这笔支付单没退过款时，后端返回 Result.fail("该支付单没有退款记录")，
//   request 会 reject —— 调用方必须把它当「还没有退款记录」，不能当故障提示。
function getRefundOrder(outTradeNo) {
  return request({ url: `/api/payments/${outTradeNo}/refund` })
}

// 取消指定的挂号订单
function cancelAppointment(id) {
  return request({ url: `/api/appointments/${id}/cancel`, method: 'PUT' })
}

// 智能导诊：根据症状描述分析推荐就诊科室（无需登录）
function triageAnalyze(symptom) {
  return request({ url: '/api/triage/analyze', method: 'POST', data: { symptom }, skipAuth: true })
}

// 就诊签到：将预约订单标记为已签到
function checkInAppointment(id) {
  return request({ url: `/api/appointments/${id}/check-in`, method: 'PUT' })
}

// 获取指定预约对应的问诊记录
function getConsultation(id) {
  return request({ url: `/api/appointments/${id}/consultation` })
}

// 按预约 ID 获取对应处方单
function getPrescriptionByAppointment(id) {
  return request({ url: `/api/appointments/${id}/prescription` })
}

// 按 ID 获取处方单详情
function getPrescription(id) {
  return request({ url: `/api/prescriptions/${id}` })
}

// 支付处方单费用（仅余额通道；微信/支付宝必须走 prepayPrescription）
function payPrescription(id, payMethod) {
  return request({ url: `/api/prescriptions/${id}/pay`, method: 'POST', data: { payMethod } })
}

// 处方费预支付：只建支付单，处方状态一分不动，等支付回调才推进到 paid。
//   PrescriptionPayHandler.onPayClosed 只记日志，处方保持 unpaid，可以重新发起缴费。
function prepayPrescription(id, payMethod) {
  return request({ url: `/api/prescriptions/${id}/prepay`, method: 'POST', data: { payMethod } })
}

// 提交预约就诊评价
function submitAppointmentReview(id, data) {
  return request({ url: `/api/appointments/${id}/review`, method: 'POST', data })
}

// 获取消息通知列表
function getNotifications(params = {}) {
  return request({ url: '/api/notifications', data: params })
}

// 获取未读消息通知数量
function getUnreadNotificationCount() {
  return request({ url: '/api/notifications/unread-count' })
}

// 将单条通知标记为已读
function markNotificationRead(id) {
  return request({ url: `/api/notifications/${id}/read`, method: 'PUT' })
}

// 将所有通知标记为已读
function markAllNotificationsRead() {
  return request({ url: '/api/notifications/read-all', method: 'PUT' })
}

//新增就诊人内容，像路由一样在这里添加内容
// 我的就诊人列表
function getPatients() {
  return request({ url: '/api/patients/mine' })
}

// 我的某个就诊人详情
function getPatientById(id) {
  return request({ url: `/api/patients/${id}` })
}

// 新增就诊人
function createPatient(data) {
  return request({ url: '/api/patients', method: 'POST', data })
}

// 编辑就诊人
function updatePatient(id, data) {
  return request({ url: `/api/patients/${id}`, method: 'PUT', data })
}

// 删除就诊人
function deletePatient(id) {
  return request({ url: `/api/patients/${id}`, method: 'DELETE' })
}

// 设为默认就诊人
function setDefaultPatient(id) {
  return request({ url: `/api/patients/${id}/default`, method: 'PUT' })
}


// ===== 医生收藏 =====
// 我的收藏医生列表
function getFavorites() {
  return request({ url: '/api/favorites' })
}

// 收藏医生（后端幂等，重复收藏不报错）
function addFavorite(doctorId) {
  return request({ url: '/api/favorites', method: 'POST', data: { doctorId } })
}

// 取消收藏医生
function removeFavorite(doctorId) {
  return request({ url: `/api/favorites/${doctorId}`, method: 'DELETE' })
}

// 查询是否已收藏某医生
function checkFavorite(doctorId) {
  return request({ url: `/api/favorites/check/${doctorId}` })
}


// ===== 模拟支付通道（Mock）=====
// 下面几个接口打的是后端 MockPayController（/mock/pay/**，permitAll），
// 它们模拟的是「用户在微信里操作收银台」这一步，后端收到后会按真实微信回调的
// 报文格式 + HMAC 签名再打回 /api/pay/notify，所以链路本身与生产完全一致。
// 模拟「用户确认支付」：后端会向 /api/pay/notify 投递一条成功的支付回调。
// 返回值里没有 data（后端返回的是 {code,message,notifyResponse}），
// 所以不能靠它的返回值判断成败，必须用 getPaymentOrder 轮询支付单状态。
function mockPaySuccess(outTradeNo) {
  return request({ url: `/mock/pay/${outTradeNo}/success`, method: 'POST' })
}

// 模拟「用户放弃支付 / 等待超时」：后端走 closeIfCreated ——
// ★ 先关支付单，再触发对应业务的 onPayClosed，各业务后果不同：
//   APPOINTMENT  → 预约 unpaid → cancelled，并回补号源（会真的取消预约！）
//   PRESCRIPTION → 只记日志，处方保持 unpaid，可重新缴费
//   RECHARGE     → 只记日志，余额一分不动
// 因此这个接口只能用于「测试关单链路」，绝不能在用户单纯关闭弹窗时调用。
function mockPayClose(outTradeNo) {
  return request({ url: `/mock/pay/${outTradeNo}/cancel`, method: 'POST' })
}

// 模拟「微信退款结果通知：成功」→ 后端把 refund_order PROCESSING→SUCCESS、
// payment_order REFUNDING→REFUNDED。
// ★ 前置条件是硬性的（MockPayController.sendRefundNotify）：支付单必须已处于 REFUNDING
//   且已有退款单，否则后端直接抛 BusinessException。也就是说必须先「取消已支付预约」，
//   让业务侧把退款发起起来，这里才有东西可推。
function mockRefundSuccess(outTradeNo) {
  return request({ url: `/mock/pay/${outTradeNo}/refund-success`, method: 'POST' })
}

// 模拟「微信退款结果通知：失败」→ refund_order PROCESSING→FAILED、
// payment_order REFUNDING→回退 PAID。
// ★ 本地环境里这个状态活不久：PaymentReconcileService 每 60s 扫一次
//   「已收款但业务单已取消」的支付单，会把它当成漏退自动重新发起退款，
//   一两分钟内又会变回 REFUNDING。这是后端对账的预期行为，不是 bug。
function mockRefundFail(outTradeNo) {
  return request({ url: `/mock/pay/${outTradeNo}/refund-fail`, method: 'POST' })
}

module.exports = {
  getDepartments,
  getDoctors,
  getDoctorById,
  getSchedules,
  getAppointments,
  getAppointmentById,
  createAppointment,
  payAppointment,
  prepayAppointment,
  getPaymentOrder,
  getRefundOrder,
  cancelAppointment,
  submitAppointmentReview,
  triageAnalyze,
  checkInAppointment,
  getConsultation,
  getPrescriptionByAppointment,
  getPrescription,
  payPrescription,
  prepayPrescription,
  getNotifications,
  getUnreadNotificationCount,
  markNotificationRead,
  markAllNotificationsRead,
  getPatients,
  getPatientById,
  createPatient,
  updatePatient,
  deletePatient,
  setDefaultPatient,
  getFavorites,
  addFavorite,
  removeFavorite,
  checkFavorite,
  // Mock 支付通道，S5 切真通道时连同上面的定义一起删除
  mockPaySuccess,
  mockPayClose,
  mockRefundSuccess,
  mockRefundFail
}
