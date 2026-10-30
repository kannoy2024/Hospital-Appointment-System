const { enrichDepartment, enrichDepartments } = require('./dept-meta')

const { resolveMediaUrl } = require('./media')

const { REGISTRATION_FEE_FEN } = require('./money')



// 标准化医生数据：补全头像路径、去掉简介末尾句号、补齐医院/评分/费用等展示字段
function enrichDoctor(doctor, hasSlot) {
  // 统一处理头像路径
  let avatar = doctor.avatar || '';
  
  // 如果 avatar 不为空且不是完整 URL
  if (avatar && !avatar.startsWith('http://') && !avatar.startsWith('https://')) {
    // 如果以 /common/ 开头，替换为 /uploads/common/
    if (avatar.startsWith('/common/')) {
      avatar = avatar.replace('/common/', '/uploads/common/');
    } 
    // 如果以 /uploads/ 开头，保持不变
    else if (avatar.startsWith('/uploads/')) {
      // 保持不变
    } 
    // 否则（如 "doctor_m_1.jpg"），补全路径
    else {
      avatar = `/uploads/common/${avatar.replace(/^\/+/, '')}`; // 去除多余斜杠
    }
  } 
  // 如果 avatar 为空，生成默认头像
  else if (!avatar) {
    // gender 兼容：'女'/2 视为女，其余（'男'/1/空）视为男
    const isFemale = doctor.gender === '女' || doctor.gender === 2
    const gender = isFemale ? 'f' : 'm';
    avatar = `/uploads/common/doctor_${gender}_${doctor.id}.jpg`;
  }
  
  const intro = (doctor.intro || '').replace(/[。.]+$/, '');
  const hasRating = doctor.rating != null && (doctor.reviewCount || 0) > 0;
  
  return {
    ...doctor,
    intro,
    hospital: '市第一人民医院',
    rating: hasRating ? Number(doctor.rating).toFixed(1) : '暂无',
    consultCount: doctor.consultCount || 0,
    // 挂号费（分）。后端 DoctorVO 不返回费用，统一取 money.REGISTRATION_FEE_FEN
    feeFen: REGISTRATION_FEE_FEN,
    hasSlot: hasSlot !== false,
    avatar,  // 保存标准化后的路径
    avatarUrl: resolveMediaUrl(avatar)  // 通过 resolveMediaUrl 拼接 baseUrl
  };
}


const MORNING_SLOTS = ['08:00', '09:00', '10:00']

const AFTERNOON_SLOTS = ['14:00', '15:00', '16:00']



// 归一化时间段文案：上午/下午映射为具体时间，其余原样返回
function normalizeSlotTime(timeSlot) {

  const t = String(timeSlot || '').trim()

  if (t === '上午') return '09:00'

  if (t === '下午') return '14:00'

  return t

}



// 将后端排班转换成号源格子的展示数据（可约/已满/无排班）
function toSlotItem(schedule) {

  const time = normalizeSlotTime(schedule.timeSlot)

  const remain = schedule.remainQuota != null

    ? schedule.remainQuota

    : (schedule.totalQuota - schedule.bookedCount)

  const active = schedule.status !== 0 && schedule.status !== '0'

  return {

    time,

    available: active && remain > 0,

    scheduleId: schedule.id,

    remainQuota: Math.max(remain, 0),

    full: active && remain <= 0,

    noSchedule: false

  }

}



// 按上午/下午模板生成号源网格，模板外的时间追加到对应半天末尾
function buildSlotGrid(schedules) {

  const slotMap = {}

  const extras = { morning: [], afternoon: [] }



  ;(schedules || []).forEach((s) => {

    const item = toSlotItem(s)

    const hour = parseInt(item.time.split(':')[0], 10) || 0

    const inTemplate = MORNING_SLOTS.includes(item.time) || AFTERNOON_SLOTS.includes(item.time)

    if (inTemplate) {

      slotMap[item.time] = item

    } else if (hour < 12) {

      extras.morning.push(item)

    } else {

      extras.afternoon.push(item)

    }

  })



  const fill = (template) => template.map((time) => slotMap[time] || {

    time,

    available: false,

    scheduleId: 0,

    remainQuota: 0,

    full: false,

    noSchedule: true

  })



  const morning = [...fill(MORNING_SLOTS), ...extras.morning]

  const afternoon = [...fill(AFTERNOON_SLOTS), ...extras.afternoon]

  const all = morning.concat(afternoon)



  return {

    morning,

    afternoon,

    hasAnySchedule: (schedules || []).length > 0,

    hasAvailable: all.some((s) => s.available)

  }

}



// 按上下午分组排班数据（内部复用号源网格构建逻辑）
function groupSchedules(schedules) {

  return buildSlotGrid(schedules)

}



// 将订单状态映射为前端展示状态（confirmed 视为 pending）
function mapAppointmentStatus(status) {

  if (status === 'confirmed') return 'pending'

  return status

}



// 解析订单展示状态：待付处方费且处方已支付时转为待发药
function resolveDisplayStatus(appt) {

  const rawStatus = appt.status

  if (rawStatus === 'pending_rx_pay' && appt.prescriptionStatus === 'paid') {

    return 'pending_dispense'

  }

  return mapAppointmentStatus(rawStatus)

}



// 将后端预约记录转换成订单展示对象，生成订单号与各类操作按钮的可用标记
function toOrder(appt) {

  // 挂号费（分）：后端 AppointmentVO.amountFen 是唯一真值，缺失时按标准挂号费兜底
  const feeFen = appt.amountFen != null ? Number(appt.amountFen) : REGISTRATION_FEE_FEN

  const rawStatus = appt.status

  const displayStatus = resolveDisplayStatus(appt)

  return {

    id: String(appt.id),

    orderNo: `AP${String(appt.id).padStart(8, '0')}`,

    doctorId: appt.doctorId || 0,

    doctorName: appt.doctorName || '',

    doctorTitle: appt.doctorTitle || '',

    departmentId: 0,

    departmentName: appt.departmentName || '',

    scheduleDate: appt.scheduleDate || '',

    timeSlot: appt.timeSlot || '',

    patientName: appt.patientName || '',

    patientPhone: appt.patientPhone || '',

    feeFen,

    payMethod: appt.payMethod || '',

    payMethodText: appt.payMethodText || '',

    payTime: appt.payTime || '',

    status: displayStatus,

    rawStatus,

    displayStatus,

    prescriptionId: appt.prescriptionId || null,

    prescriptionStatus: appt.prescriptionStatus || '',

    prescriptionAmountFen: appt.prescriptionAmountFen != null ? Number(appt.prescriptionAmountFen) : 0,

    hasConsultation: !!appt.hasConsultation,

    canPay: rawStatus === 'unpaid',

    canCheckIn: rawStatus === 'pending' || rawStatus === 'confirmed',

    canCancel: ['unpaid', 'pending', 'confirmed'].includes(rawStatus),

    canPayPrescription: rawStatus === 'pending_rx_pay' && appt.prescriptionStatus === 'unpaid',

    canViewRecord: !!appt.hasConsultation,

    canViewPrescription: !!appt.prescriptionId,

    location: appt.departmentName ? `门诊楼 ${appt.departmentName}诊区` : '',

    remark: appt.remark || '',

    createTime: appt.createTime || '',

    evaluated: !!appt.evaluated

  }

}



module.exports = {

  enrichDepartment,

  enrichDepartments,

  enrichDoctor,

  buildSlotGrid,

  groupSchedules,

  toOrder,

  resolveDisplayStatus

}

