const api = require('./api')

const PAY_METHODS = [
  { id: 'wechat', name: '微信支付', desc: '模拟支付', icon: 'wechat', logo: '' },
  // 支付宝入口先保留占位，但这轮不实现：选中时只提示，不发起任何支付请求
  { id: 'alipay', name: '支付宝', desc: '暂未接入', icon: 'alipay', logo: '', disabled: true },
  { id: 'balance', name: '余额支付', desc: '', icon: 'balance', logo: '' }
]

const PAY_CHANNEL_INFO = {
  wechat: { name: '微信支付', logo: '' },
  alipay: { name: '支付宝', logo: '' },
  balance: { name: '余额支付', logo: '' }
}

// 将支付方式 id 转成中文名称，未知时返回原值
function payMethodLabel(method) {
  const map = {
    balance: '余额支付',
    wechat: '微信支付',
    alipay: '支付宝'
  }
  return map[method] || method || ''
}

// 获取支付渠道信息（名称/logo），未匹配时默认返回微信支付
function getPayChannelInfo(method) {
  return PAY_CHANNEL_INFO[method] || PAY_CHANNEL_INFO.wechat
}

// 判断是否为第三方渠道支付（微信/支付宝）
function isChannelPay(method) {
  return method === 'wechat' || method === 'alipay'
}

// 判断该支付方式是否需要弹出支付抽屉
function needsPayDrawer(method) {
  return method === 'wechat' || method === 'alipay' || method === 'balance'
}

// 是否为余额通道：余额走 /api/appointments/{id}/pay 直接扣款，一步完成；
// 微信/支付宝必须走 /prepay 建支付单，再由回调推进
function isBalancePay(method) {
  return method === 'balance'
}

// 支付单终态：到达其中任意一个就不再轮询
const PAYMENT_TERMINAL_STATUSES = ['PAID', 'CLOSED', 'FAILED', 'REFUNDED']

// 支付单状态 -> 中文（仅用于提示文案）
const PAYMENT_STATUS_TEXT = {
  CREATED: '待支付',
  PAID: '支付成功',
  CLOSED: '支付单已关闭',
  FAILED: '支付失败',
  REFUNDING: '退款中',
  REFUNDED: '已退款'
}

// 轮询支付单，直到进入终态或次数用尽。
// ★ 为什么必须轮询：prepay 只负责建单，预约状态由网关回调驱动地推进，
//   回调是后端的异步动作，前端发完「模拟支付」后并不知道它什么时候落库。
// ★ 为什么必须 catch：GET /api/payments/{outTradeNo} 在支付单尚未提交可见、
//   或越权/不存在时返回 Result.fail，request.js 会 reject。一次瞬时失败
//   不应该让整笔支付直接判死，所以这里把错误记录下来继续重试。
function pollPaymentOrder(outTradeNo, options = {}) {
  const interval = options.interval || 600
  const maxAttempts = options.maxAttempts || 15
  return new Promise((resolve, reject) => {
    let attempts = 0
    let lastError = null

    const schedule = () => {
      attempts += 1
      if (attempts > maxAttempts) {
        reject(lastError || new Error('支付结果确认超时，请稍后在订单列表刷新查看'))
        return
      }
      api.getPaymentOrder(outTradeNo)
        .then((order) => {
          if (order && PAYMENT_TERMINAL_STATUSES.indexOf(order.status) !== -1) {
            resolve(order)
            return
          }
          setTimeout(schedule, interval)
        })
        .catch((err) => {
          lastError = err
          setTimeout(schedule, interval)
        })
    }

    // 先立即查一次：回调很快时无需等待第一个 interval
    schedule()
  })
}

// 退款单状态 -> 中文（仅用于展示文案，取值见后端 RefundOrder）
const REFUND_STATUS_TEXT = {
  PROCESSING: '退款处理中',
  SUCCESS: '退款成功',
  FAILED: '退款失败',
  CLOSED: '退款已关闭'
}

// 本地缓存「业务单 → 支付单号」的映射。
//
// ★ 为什么需要它：AppointmentVO / PrescriptionVO 都不带 outTradeNo，后端也没有
//   「按业务单查支付单」的对用户接口（findPaidOutTradeNo 是 PaymentService 的内部方法），
//   而查退款状态（GET /api/payments/{outTradeNo}/refund）和驱动两个 Mock 退款回调
//   都必须拿到 outTradeNo。只能在前端把预支付时拿到的单号记下来。
//
// ★ 已知局限（不是 bug，是后端没暴露该字段）：只有「在本机、本版本之后发起过预支付」
//   的业务单才有记录；历史单、换设备支付的单、清过小程序缓存的单都查不到退款。
//   等后端在 AppointmentVO 上补了 outTradeNo，把 getOutTradeNo 改成读接口即可，
//   调用方（页面）一行都不用动。
const PAY_TRADE_NO_PREFIX = 'pay:outTradeNo:'

function payTradeNoKey(bizType, bizId) {
  return `${PAY_TRADE_NO_PREFIX}${bizType}:${bizId}`
}

// 记下业务单对应的支付单号（预支付成功后立即调用）
function saveOutTradeNo(bizType, bizId, outTradeNo) {
  if (!bizType || bizId == null || bizId === '' || !outTradeNo) return
  try {
    wx.setStorageSync(payTradeNoKey(bizType, bizId), outTradeNo)
  } catch (e) {
    // 缓存失败不影响支付主流程，最坏结果是之后看不到这单的退款进度
  }
}

// 取业务单对应的支付单号，没有记录时返回空串
function getOutTradeNo(bizType, bizId) {
  if (!bizType || bizId == null || bizId === '') return ''
  try {
    return wx.getStorageSync(payTradeNoKey(bizType, bizId)) || ''
  } catch (e) {
    return ''
  }
}

module.exports = {
  PAY_METHODS,
  PAY_CHANNEL_INFO,
  PAYMENT_TERMINAL_STATUSES,
  PAYMENT_STATUS_TEXT,
  REFUND_STATUS_TEXT,
  payMethodLabel,
  getPayChannelInfo,
  isChannelPay,
  needsPayDrawer,
  isBalancePay,
  pollPaymentOrder,
  saveOutTradeNo,
  getOutTradeNo
}
