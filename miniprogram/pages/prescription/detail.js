const api = require('../../utils/api')
const auth = require('../../utils/auth')
const money = require('../../utils/money')
const {
  PAY_METHODS,
  PAYMENT_STATUS_TEXT,
  needsPayDrawer,
  isBalancePay,
  pollPaymentOrder
} = require('../../utils/pay')
const { formatServerDateTime } = require('../../utils/format')
const toast = require('../../utils/toast')

// 单条药品金额（分）。
// 后端 PrescriptionItem 实体的字段是 amountFen（@TableField("amount_fen")），
// 但 PrescriptionItemVO 里把它声明成了 amount，BeanUtils.copyProperties 拷不过去，
// 所以 item.amountFen 目前恒为 null —— 这里先用 unitPriceFen × quantity 兜底；
// 后端把 PrescriptionItemVO.java:19 改成 `private Long amountFen;` 之后即可直接取用。
function itemAmountFen(item) {
  if (item.amountFen != null) return Number(item.amountFen)
  return Number(item.unitPriceFen || 0) * Number(item.quantity || 0)
}

Page({
  data: {
    prescription: null,
    items: [],
    amountText: '',
    // 传给 pay-drawer / pay-sheet 的元文本（不带 ¥，组件自己会补符号）
    amountYuanText: '',
    canPay: false,
    // 余额唯一真值（分）；balanceText 是它的元展示形式
    balanceFen: 0,
    balanceText: '0.00',
    payMethods: PAY_METHODS,
    payMethod: 'wechat',
    payDrawerVisible: false,
    payDrawerMethod: 'wechat',
    paying: false,
    // ===== 模拟收银台上下文（微信/支付宝通道）=====
    paySheetVisible: false,
    paySheetBusy: false,
    paySheetMethod: 'wechat',
    paySheetOutTradeNo: '',
    paySheetDescription: '',
    paySheetAmountText: '',
    paySheetExpireText: ''
  },

  // 页面加载时记录路由参数，并加载处方数据和账户余额
  onLoad(options) {
    this.appointmentId = options.appointmentId
    this.prescriptionId = options.id
    this.loadData()
    this.loadBalance()
  },

  // 拉取账户余额并在支付方式里展示可用余额
  loadBalance() {
    if (!auth.isLoggedIn()) return
    auth.fetchMe().then((user) => {
      const balanceFen = user && user.balanceFen != null ? Number(user.balanceFen) : 0
      const balanceText = money.fenToYuanText(balanceFen)
      const payMethods = PAY_METHODS.map((item) => ({
        ...item,
        desc: item.id === 'balance' ? `可用 ¥${balanceText}` : item.desc
      }))
      this.setData({ balanceFen, balanceText, payMethods })
    }).catch(() => {})
  },

  // 按处方 id 或预约 id 加载处方详情并组装药品明细
  loadData() {
    const loader = this.prescriptionId
      ? api.getPrescription(this.prescriptionId)
      : api.getPrescriptionByAppointment(this.appointmentId)
    loader
      .then((rx) => {
        if (!rx) {
          toast('暂无处方')
          return
        }
        const list = (rx.items || []).map((item) => ({
          label: `${item.medicineName} ${item.spec || ''}`,
          value: `${item.dosage || ''} ${item.frequency || ''} ×${item.quantity}  ${money.fenToYuanLabel(itemAmountFen(item))}`
        }))
        this.setData({
          prescription: rx,
          items: list,
          amountText: money.fenToYuanLabel(rx.totalAmountFen),
          amountYuanText: money.fenToYuanText(rx.totalAmountFen),
          canPay: rx.status === 'unpaid'
        })
      })
      .catch((err) => toast(err.message || '加载失败'))
  },

  // 选择支付方式，若需二次确认则弹出支付抽屉
  onPayMethodTap(e) {
    const { id } = e.currentTarget.dataset
    if (!id) return
    const method = PAY_METHODS.find((item) => item.id === id)
    if (method && method.disabled) {
      toast(`${method.name}暂未接入，当前只开放微信支付与余额支付`)
      return
    }
    this.setData({ payMethod: id })
    if (needsPayDrawer(id)) {
      this.setData({ payDrawerVisible: true, payDrawerMethod: id })
    }
  },

  // 关闭支付确认抽屉
  onPayDrawerClose() {
    this.setData({ payDrawerVisible: false })
  },

  // 支付抽屉确认，执行支付流程
  onPayDrawerConfirm() {
    this.doPay()
  },

  // 点击支付按钮，校验可支付状态后直接支付或先弹确认抽屉
  onPay() {
    if (!this.data.canPay || this.data.paying || this.data.paySheetVisible) return
    const { payMethod } = this.data
    if (needsPayDrawer(payMethod)) {
      this.setData({ payDrawerVisible: true, payDrawerMethod: payMethod })
      return
    }
    this.doPay()
  },

  // 支付：两条互斥链路
  //   余额        → POST /api/prescriptions/{id}/pay，后端同一事务内扣款并把处方推进到 paid，一步到位
  //   微信/支付宝 → 先 prepay 建支付单（处方仍是 unpaid），用户在模拟收银台确认后，
  //                回调落到后端才真正推进业务状态
  async doPay() {
    const { prescription, payMethod } = this.data
    if (!prescription || this.data.paying) return

    if (isBalancePay(payMethod)) {
      // 处方总额与余额都在「分」域内比较，不经过元也不经过浮点
      if (Number(prescription.totalAmountFen) > this.data.balanceFen) {
        toast('余额不足，请先充值')
        return
      }
      this.setData({ paying: true })
      try {
        await api.payPrescription(prescription.id, payMethod)
        await auth.fetchMe()
        this.setData({ payDrawerVisible: false })
        wx.showToast({ title: '支付成功，请等待取药', icon: 'success' })
        this.loadData()
        this.loadBalance()
        setTimeout(() => {
          const pages = getCurrentPages()
          if (pages.length > 1) wx.navigateBack()
        }, 800)
      } catch (err) {
        toast(err.message || '支付失败')
      } finally {
        this.setData({ paying: false })
      }
      return
    }

    // 第三方渠道：预支付只负责建单，拿不到支付结果，必须靠后面的轮询
    this.setData({ paying: true })
    try {
      const prepay = await api.prepayPrescription(prescription.id, payMethod)
      this.setData({
        paying: false,
        payDrawerVisible: false,
        paySheetVisible: true,
        paySheetBusy: false,
        paySheetMethod: payMethod,
        paySheetOutTradeNo: prepay.outTradeNo,
        paySheetDescription: '处方药费',
        paySheetAmountText: money.fenToYuanText(prepay.amountFen),
        paySheetExpireText: formatServerDateTime(prepay.expireTime)
      })
    } catch (err) {
      this.setData({ paying: false, payDrawerVisible: false })
      toast(err.message || '发起支付失败')
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
  // ★ 成败只看轮询出来的支付单状态，不看 mockPaySuccess 的返回值 ——
  //   后端那个接口返回的是 {code,message,notifyResponse}，没有 data，resolve 出来是 undefined。
  async onPaySheetConfirm() {
    const { paySheetBusy, paySheetOutTradeNo, prescription } = this.data
    if (paySheetBusy || !paySheetOutTradeNo || !prescription) return
    this.setData({ paySheetBusy: true })

    let payment = null
    try {
      await api.mockPaySuccess(paySheetOutTradeNo)
      payment = await pollPaymentOrder(paySheetOutTradeNo)
    } catch (err) {
      // 回调投递失败或轮询超时：两者对用户来说是同一件事 —— 结果未确认，回处方页刷新
      this.closePaySheet()
      toast(err.message || '支付结果确认失败，请稍后刷新处方查看')
      this.loadData()
      return
    }

    this.closePaySheet()
    if (payment.status === 'PAID') {
      wx.showToast({ title: '支付成功，请等待取药', icon: 'success' })
      this.loadData()
      this.loadBalance()
      setTimeout(() => {
        const pages = getCurrentPages()
        if (pages.length > 1) wx.navigateBack()
      }, 800)
      return
    }
    toast(`${PAYMENT_STATUS_TEXT[payment.status] || '支付未完成'}，请稍后刷新处方`)
    this.loadData()
  },

  // 收银台「取消」：只收起本次支付行为，绝不去动后端支付单。
  // 处方没有「超时作废」语义，即使调了 /mock/pay/{outTradeNo}/cancel，
  // 后端 PrescriptionPayHandler.onPayClosed 也只记日志、不动处方状态。
  onPaySheetCancel() {
    if (this.data.paySheetBusy) return
    this.closePaySheet()
    toast('已取消本次支付，处方仍可重新缴费')
  },

  // ⚠️ 测试专用：模拟支付超时。
  // 会真正走后端关单逻辑（先网关关单、再 CAS 置 CLOSED、再 onPayClosed），
  // 但处方**不会**被取消 —— 与挂号不同，处方支付单关闭后处方保持 unpaid，可重新发起缴费。
  async onPaySheetMockTimeout() {
    const { paySheetBusy, paySheetOutTradeNo, prescription } = this.data
    if (paySheetBusy || !paySheetOutTradeNo || !prescription) return
    this.setData({ paySheetBusy: true })
    try {
      await api.mockPayClose(paySheetOutTradeNo)
      this.closePaySheet()
      wx.showModal({
        title: '已模拟支付超时',
        content: '支付单已关闭。处方保持待支付状态，可以重新发起缴费；这是关单链路的真实执行结果。',
        showCancel: false
      })
      this.loadData()
    } catch (err) {
      this.setData({ paySheetBusy: false })
      toast(err.message || '模拟支付超时失败')
    }
  },

  // ⚠️ 测试专用：模拟支付失败。纯前端行为，不发任何请求，
  // 因此支付单保持 CREATED、处方仍是 unpaid；重新发起时后端 prepay 有可重入护栏，
  // 会复用同一张支付单，不会堆出多张单。
  onPaySheetMockFail() {
    if (this.data.paySheetBusy) return
    this.closePaySheet()
    toast('已模拟支付失败（未请求后端，处方仍可缴费）')
  }
})
