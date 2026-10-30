const api = require('../../utils/api')
const auth = require('../../utils/auth')
const walletApi = require('../../utils/wallet-api')
const { PAY_METHODS, PAYMENT_STATUS_TEXT, pollPaymentOrder } = require('../../utils/pay')
const toast = require('../../utils/toast')
const { formatServerDateTime } = require('../../utils/format')
const money = require('../../utils/money')

const RECHARGE_PAY_METHODS = PAY_METHODS.filter((item) => item.id === 'wechat' || item.id === 'alipay')

// 把用户余额（后端返回 balanceFen，单位分）格式化为两位小数字符串，无余额时返回 0.00
function formatBalance(user) {
  if (!user || user.balanceFen == null) return '0.00'
  return money.fenToYuanText(user.balanceFen)
}

Page({
  data: {
    balanceText: '0.00',
    amountOptions: [50, 100, 200, 500],
    selectedAmount: 100,
    customAmount: '',
    // 充值金额的唯一真值，单位：分。rechargeAmountText 只是它的元展示形式
    rechargeAmountFen: 10000,
    rechargeAmountText: '100.00',
    payMethods: RECHARGE_PAY_METHODS,
    payMethod: 'wechat',
    payDrawerVisible: false,
    payDrawerMethod: 'wechat',
    submitting: false,
    // ===== 模拟收银台上下文 =====
    paySheetVisible: false,
    paySheetBusy: false,
    paySheetMethod: 'wechat',
    paySheetOutTradeNo: '',
    paySheetDescription: '',
    paySheetAmountText: '',
    paySheetExpireText: '',
    logs: [],
    logsLoading: false
  },

  // 页面显示时触发，校验登录态并刷新余额与充值记录
  onShow() {
    if (!auth.isLoggedIn()) {
      toast('请先登录')
      setTimeout(() => wx.switchTab({ url: '/pages/mine/mine' }), 500)
      return
    }
    this.refreshBalance()
    this.loadLogs()
  },

  // 重新拉取用户信息并更新页面显示的余额
  async refreshBalance() {
    try {
      const user = await auth.fetchMe()
      this.setData({ balanceText: formatBalance(user) })
    } catch (e) {
      toast(e.message || '加载余额失败')
    }
  },

  // 加载最近 10 条充值流水并格式化金额与时间
  async loadLogs() {
    this.setData({ logsLoading: true })
    try {
      const result = await walletApi.fetchBalanceLogs({ current: 1, size: 10 })
      const logs = (result.records || [])
        .filter((item) => item.type === 'recharge')
        .map((item) => ({
          ...item,
          // typeText 由后端 BalanceLogVO.typeLabel 直接给出，前端不再自行拼接
          // 金额走「分」，取绝对值避免出现 "+¥-20.00"
          amountText: money.fenToYuanText(Math.abs(Number(item.amountFen) || 0)),
          createTimeText: formatServerDateTime(item.createTime)
        }))
      this.setData({ logs })
    } catch (e) {
      this.setData({ logs: [] })
    } finally {
      this.setData({ logsLoading: false })
    }
  },

  // 根据选中的固定金额（单位：元）或自定义金额（单位：元）换算出最终充值金额（单位：分）
  updateRechargeAmount() {
    const { selectedAmount, customAmount } = this.data
    let amountFen = money.yuanToFen(selectedAmount)
    if (customAmount) {
      const customFen = money.yuanToFen(customAmount)
      if (customFen > 0) amountFen = customFen
    }
    this.setData({
      rechargeAmountFen: amountFen,
      rechargeAmountText: money.fenToYuanText(amountFen)
    })
  },

  // 点击固定金额选项时选中该金额并清空自定义输入
  onAmountTap(e) {
    const amount = Number(e.currentTarget.dataset.amount)
    if (!amount) return
    this.setData({ selectedAmount: amount, customAmount: '' })
    this.updateRechargeAmount()
  },

  // 自定义金额输入时更新金额并取消固定金额选中（小数位交给 updateRechargeAmount 统一进位到分）
  onCustomAmountChange(e) {
    this.setData({ customAmount: String(e.detail || '').trim(), selectedAmount: 0 })
    this.updateRechargeAmount()
  },

  // 选择支付方式并打开支付确认抽屉
  onPayMethodTap(e) {
    const { id } = e.currentTarget.dataset
    if (!id) return
    const method = PAY_METHODS.find((item) => item.id === id)
    if (method && method.disabled) {
      toast(`${method.name}暂未接入，当前只开放微信支付`)
      return
    }
    this.setData({ payMethod: id, payDrawerVisible: true, payDrawerMethod: id })
  },

  // 关闭支付确认抽屉
  onPayDrawerClose() {
    this.setData({ payDrawerVisible: false })
  },

  // 确认支付，执行充值流程
  onPayDrawerConfirm() {
    this.doRecharge()
  },

  // 点击充值按钮，校验金额后打开支付方式抽屉
  onRechargeTap() {
    if (this.data.paySheetVisible) return
    if (!this.validateAmount()) return
    this.setData({ payDrawerVisible: true, payDrawerMethod: this.data.payMethod })
  },

  // 校验当前充值金额（单位分）是否合法，不合法时提示并返回 false
  validateAmount() {
    const amountFen = this.data.rechargeAmountFen
    if (!money.isValidFen(amountFen)) {
      toast('请输入有效充值金额，最低 0.01 元')
      return false
    }
    if (amountFen > money.MAX_RECHARGE_FEN) {
      toast(`单次充值不能超过 ${money.fenToYuanText(money.MAX_RECHARGE_FEN)} 元`)
      return false
    }
    return true
  },

  // 发起充值预支付。
  // ★ 后端 POST /api/wallet/recharge 现在只建支付单，余额一分不动，
  //   必须等支付回调落到 WalletRechargeHandler.markPaid 才入账 —— 所以这里拿不到余额结果，
  //   只能拉起收银台，再由轮询确认结果。
  async doRecharge() {
    if (this.data.submitting) return
    if (!this.validateAmount()) return
    const amountFen = this.data.rechargeAmountFen
    this.setData({ submitting: true })
    try {
      const prepay = await walletApi.recharge(amountFen, this.data.payMethod)
      this.setData({
        submitting: false,
        payDrawerVisible: false,
        paySheetVisible: true,
        paySheetBusy: false,
        paySheetMethod: this.data.payMethod,
        paySheetOutTradeNo: prepay.outTradeNo,
        paySheetDescription: '账户充值',
        // 金额以支付单为准，不用页面上的本地值，避免「显示 100 实际充 1 分」这类错位
        paySheetAmountText: money.fenToYuanText(prepay.amountFen),
        paySheetExpireText: formatServerDateTime(prepay.expireTime)
      })
    } catch (e) {
      this.setData({ submitting: false, payDrawerVisible: false })
      toast(e.message || '发起充值失败')
    }
  },

  // 收起收银台并清掉这一轮的支付单上下文
  closePaySheet() {
    this.setData({
      paySheetVisible: false,
      paySheetBusy: false,
      paySheetMethod: 'wechat',
      paySheetOutTradeNo: '',
      paySheetDescription: '',
      paySheetAmountText: '',
      paySheetExpireText: ''
    })
  },

  // 收银台「确认支付」：让后端 Mock 网关投递一条真实格式的支付回调，再轮询支付单直到终态。
  // ★ 成败只看轮询出来的支付单状态，不看 mockPaySuccess 的返回值。
  async onPaySheetConfirm() {
    const { paySheetBusy, paySheetOutTradeNo } = this.data
    if (paySheetBusy || !paySheetOutTradeNo) return
    this.setData({ paySheetBusy: true })

    let payment = null
    try {
      await api.mockPaySuccess(paySheetOutTradeNo)
      payment = await pollPaymentOrder(paySheetOutTradeNo)
    } catch (e) {
      this.closePaySheet()
      toast(e.message || '支付结果确认失败，请稍后刷新余额查看')
      this.refreshBalance()
      return
    }

    this.closePaySheet()
    if (payment.status === 'PAID') {
      // 充值到账后余额与流水都要刷新：余额来自 WalletRechargeHandler.applyRecharge
      await this.refreshBalance()
      this.setData({ customAmount: '', selectedAmount: 100 })
      this.updateRechargeAmount()
      toast.success('充值成功')
      this.loadLogs()
      return
    }
    toast(`${PAYMENT_STATUS_TEXT[payment.status] || '支付未完成'}，余额未变动`)
    this.refreshBalance()
  },

  // 收银台「取消」：只收起本次支付行为，绝不去动后端支付单。
  // 充值单被关闭时 WalletRechargeHandler.onPayClosed 只记日志，余额一分不动。
  onPaySheetCancel() {
    if (this.data.paySheetBusy) return
    this.closePaySheet()
    toast('已取消本次充值，余额未变动')
  },

  // ⚠️ 测试专用：模拟支付超时。
  // 会真正走后端关单逻辑（先网关关单、再 CAS 置 CLOSED、再 onPayClosed），
  // 但充值单关闭不会加余额 —— 余额保持原样，可以重新发起充值。
  async onPaySheetMockTimeout() {
    const { paySheetBusy, paySheetOutTradeNo } = this.data
    if (paySheetBusy || !paySheetOutTradeNo) return
    this.setData({ paySheetBusy: true })
    try {
      await api.mockPayClose(paySheetOutTradeNo)
      this.closePaySheet()
      wx.showModal({
        title: '已模拟支付超时',
        content: '充值单已关闭，余额未变动。这是关单链路的真实执行结果，可以重新发起充值。',
        showCancel: false
      })
      this.refreshBalance()
    } catch (e) {
      this.setData({ paySheetBusy: false })
      toast(e.message || '模拟支付超时失败')
    }
  },

  // ⚠️ 测试专用：模拟支付失败。纯前端行为，不发任何请求，
  // 因此充值单保持 CREATED、余额不变。
  // ★ 充值单走 PrepayCommand#reuseExistingCreated=false，重新充值会建新单而不是复用旧单，
  //   这是刻意的：同一用户连续充两笔不能被合并成一笔。
  onPaySheetMockFail() {
    if (this.data.paySheetBusy) return
    this.closePaySheet()
    toast('已模拟支付失败（未请求后端，余额未变动）')
  }
})
