const WEEK_LABELS = ['日', '一', '二', '三', '四', '五', '六']

const STATUS_MAP = {
  available: { text: '有号', type: 'success' },
  full: { text: '已满', type: 'default' },
  unpaid: { text: '待支付', type: 'danger' },
  pending: { text: '待就诊', type: 'warning' },
  confirmed: { text: '待就诊', type: 'warning' },
  checked_in: { text: '已到院', type: 'primary' },
  in_consultation: { text: '就诊中', type: 'warning' },
  pending_rx_pay: { text: '待缴药费', type: 'danger' },
  pending_dispense: { text: '待取药', type: 'warning' },
  completed: { text: '已完成', type: 'success' },
  cancelled: { text: '已取消', type: 'danger' }
}

const VISIT_STEPS = [
  { key: 'pending', label: '已预约' },
  { key: 'checked_in', label: '已到院' },
  { key: 'in_consultation', label: '就诊中' },
  { key: 'pending_rx_pay', label: '待缴药费' },
  { key: 'pending_dispense', label: '待取药' },
  { key: 'completed', label: '已完成' }
]

// 根据订单状态返回它在就诊进度条中的步骤下标
function getVisitStepIndex(status) {
  const map = {
    unpaid: 0,
    pending: 0,
    confirmed: 0,
    checked_in: 1,
    in_consultation: 2,
    pending_rx_pay: 3,
    pending_dispense: 4,
    completed: 5,
    cancelled: -1
  }
  return map[status] != null ? map[status] : 0
}

// 数字补零到两位（如 5 → "05"）
function pad(n) {
  return n < 10 ? `0${n}` : `${n}`
}

/** 后端 LocalDateTime：ISO 字符串或 [y,m,d,h,mi,s] 数组 */
function formatServerDateTime(value) {
  if (value == null || value === '') return ''
  if (Array.isArray(value)) {
    const [y, mo, d, h = 0, mi = 0] = value
    return `${y}-${pad(mo)}-${pad(d)} ${pad(h)}:${pad(mi)}`
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

// 把 Date 或日期字符串格式化为 "YYYY-MM-DD"
function formatDate(date) {
  const d = date instanceof Date ? date : new Date(date)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// 把日期+时段格式化为 "X月X日 周几 09:00" 的展示文案
function formatDateTime(dateStr, timeSlot) {
  if (!dateStr) return ''
  const d = new Date(dateStr.replace(/-/g, '/'))
  const week = WEEK_LABELS[d.getDay()]
  return `${pad(d.getMonth() + 1)}月${pad(d.getDate())}日 周${week} ${timeSlot || ''}`
}

// 根据日期返回"今天/明天/周几"标签
function getWeekLabel(dateStr) {
  const d = new Date(dateStr.replace(/-/g, '/'))
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const target = new Date(d)
  target.setHours(0, 0, 0, 0)
  const diff = (target - today) / 86400000
  if (diff === 0) return '今天'
  if (diff === 1) return '明天'
  return `周${WEEK_LABELS[d.getDay()]}`
}

// 生成从今天起连续 N 天的日期条数据（供排班页日期选择用）
function buildDateStrip(days = 7) {
  const list = []
  const base = new Date()
  base.setHours(0, 0, 0, 0)
  for (let i = 0; i < days; i++) {
    const d = new Date(base)
    d.setDate(base.getDate() + i)
    const value = formatDate(d)
    list.push({
      value,
      week: getWeekLabel(value),
      day: pad(d.getDate()),
      month: `${d.getMonth() + 1}月`
    })
  }
  return list
}

// 根据状态码取对应的展示文案和颜色类型（查不到时返回兜底值）
function getStatusInfo(status) {
  return STATUS_MAP[status] || { text: status, type: 'default' }
}

// 注意：金额格式化已迁到 utils/money.js
// （原 formatFee(amount) 不区分入参是元还是分，后端全面改用「分」之后极易放大 100 倍，
//  故直接删除，改为 money.fenToYuanLabel(分) / money.fenToYuanText(分) 显式表达单位）

// 生成订单号（时间戳 + 随机数，仅本地展示用）
function genOrderNo() {
  return `AP${Date.now()}${Math.floor(Math.random() * 1000)}`
}

module.exports = {
  WEEK_LABELS,
  STATUS_MAP,
  formatDate,
  formatDateTime,
  formatServerDateTime,
  getWeekLabel,
  buildDateStrip,
  getStatusInfo,
  genOrderNo,
  VISIT_STEPS,
  getVisitStepIndex
}
