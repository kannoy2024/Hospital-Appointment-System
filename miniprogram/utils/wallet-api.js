const { request } = require('./request')

// 发起充值预支付（不是「充值」，余额此时一分不动）。
// 返回 PaymentPrepayVO：{ bizType, bizId, outTradeNo, payMethod, amountFen, expireTime, payParams }。
// ★ 真正的入账发生在支付回调里（WalletRechargeHandler.markPaid → applyRecharge），
//   所以调用方必须用 outTradeNo 轮询 GET /api/payments/{outTradeNo} 确认结果，不能看返回值判断成功。
// 金额单位是「分」（后端 WalletRechargeRequest.amountFen 为 Long，@Min(1)），
// 传元会被后端当成分处理，务必先在页面层用 money.yuanToFen 换算。
function recharge(amountFen, payMethod) {
  return request({
    url: '/api/wallet/recharge',
    method: 'POST',
    data: { amountFen: Number(amountFen), payMethod }
  })
}

// 分页获取余额变动记录，默认第 1 页每页 20 条
function fetchBalanceLogs(params = {}) {
  return request({
    url: '/api/wallet/logs',
    data: {
      current: params.current || 1,
      size: params.size || 20
    }
  })
}

module.exports = {
  recharge,
  fetchBalanceLogs
}
