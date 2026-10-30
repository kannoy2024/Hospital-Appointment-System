const ROLE_LABELS = {
  ADMIN: '管理员',
  USER: '用户'
}

const ROLE_TAG_TYPES = {
  ADMIN: 'danger',
  USER: 'info'
}

/**
 * 格式化日期时间，ISO 转为 yyyy-MM-dd HH:mm:ss
 */
export function formatDateTime(value) {
  if (value == null || value === '') {
    return '-'
  }
  const text = String(value).replace('T', ' ').replace(/\.\d+/, '')
  return text.length > 19 ? text.slice(0, 19) : text
}

export function roleLabel(role, roleOptions) {
  if (roleOptions?.length) {
    const found = roleOptions.find((r) => r.code === role)
    if (found) {
      return found.name
    }
  }
  return ROLE_LABELS[role] || role || '-'
}

export function roleTagType(role) {
  return ROLE_TAG_TYPES[role] || 'info'
}

export function statusLabel(status) {
  return status === 1 ? '启用' : '禁用'
}

/* ==================== 金额工具 ====================
 * 约定（与后端 MoneyUtils、小程序 utils/money.js 一致）：
 *   前后端之间传输、以及业务里做加减的金额，一律是「分」为单位的整数，字段名带 Fen 后缀
 *   （priceFen / amountFen / totalAmountFen ...）；只有渲染到界面上时才转成「元」。
 *
 * 不要用 Number(x) / 100 去拼字符串：0.07 * 100 === 7.000000000000001，
 * 所以下面全程只做字符串拆分与整数运算。
 */

/** 元 → 分（HALF_UP 四舍五入，与后端 MoneyUtils.yuanToFen 对齐；非法输入返回 0） */
export function yuanToFen(input) {
  const text = String(input ?? '').trim()
  if (!/^\d+(\.\d*)?$|^\.\d+$/.test(text)) {
    return 0
  }
  const [intPart = '', decPart = ''] = text.split('.')
  const intFen = Number(intPart || '0') * 100
  // 多取一位小数用于四舍五入
  const cents = `${decPart}000`.slice(0, 3)
  let decFen = Number(cents.slice(0, 2))
  if (Number(cents.charAt(2)) >= 5) {
    decFen += 1
  }
  return intFen + decFen
}

/** 分 → 元文本（固定两位小数，不带货币符号） */
export function fenToYuanText(fen) {
  const value = Math.trunc(Number(fen) || 0)
  const sign = value < 0 ? '-' : ''
  const abs = Math.abs(value)
  return `${sign}${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
}

/** 分 → 带 ¥ 的元文本，用于界面展示 */
export function fenToYuanLabel(fen) {
  return `¥${fenToYuanText(fen)}`
}
