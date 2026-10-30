const api = require('../../utils/api')
const auth = require('../../utils/auth')
const { toOrder } = require('../../utils/adapter')
const { formatDateTime, formatServerDateTime } = require('../../utils/format')
const money = require('../../utils/money')
const {
  PAY_METHODS,
  PAYMENT_STATUS_TEXT,
  REFUND_STATUS_TEXT,
  needsPayDrawer,
  isBalancePay,
  isChannelPay,
  pollPaymentOrder,
  saveOutTradeNo,
  getOutTradeNo
} = require('../../utils/pay')
const toast = require('../../utils/toast')

Page({
  data: {
    order: null,
    detailItems: [],
    feeText: '',
    // 传给 pay-drawer 的元文本（不带 ¥，组件自己会补符号）
    feeAmountText: '',
    rxFeeText: '',
    // 余额唯一真值（分）；balanceText 是它的元展示形式
    balanceFen: 0,
    balanceText: '0.00',
    payMethods: PAY_METHODS,
    payMethod: 'wechat',
    payDrawerVisible: false,
    payDrawerMethod: 'wechat',
    paying: false,
    // 模拟收银台（pay-sheet）。outTradeNo 是 backend prepay 建出来的支付单号，
    // 它才是这一轮「支付」的身份证；预约单 id 在这条链路上已经不起作用了。
    paySheetVisible: false,
    paySheetBusy: false,
    paySheetMethod: 'wechat',
    paySheetOutTradeNo: '',
    paySheetDescription: '',
    paySheetAmountText: '',
    paySheetExpireText: '',
    // 退款卡片。退款不是前端发起的：取消「已支付」预约时后端自己把预约改成 cancelled
    // 并把支付单推进到 REFUNDING（微信/支付宝异步；余额在取消时已同步退回，没有退款单）。
    // 前端只做两件事：展示退款进度、在 Mock 通道里模拟网关的退款结果回调。
    refundVisible: false,
    refund: null,
    refundTip: '',
    refundOutTradeNo: '',
    refundBusy: false
  },

  // 页面加载时根据路由参数加载订单详情
  onLoad(options) {
    this.loadOrder(options.id)
  },

  // 页面每次显示时刷新订单详情和账户余额
  onShow() {
    if (this.orderId) this.loadOrder(this.orderId)
    this.loadBalance()
  },

  // 拉取账户余额并在支付方式里展示可用余额
  async loadBalance() {
    if (!auth.isLoggedIn()) return
    try {
      const user = await auth.fetchMe()
      const balanceFen = user && user.balanceFen != null ? Number(user.balanceFen) : 0
      const balanceText = money.fenToYuanText(balanceFen)
      const payMethods = PAY_METHODS.map((item) => ({
        ...item,
        desc: item.id === 'balance' ? `可用 ¥${balanceText}` : item.desc
      }))
      this.setData({ balanceFen, balanceText, payMethods })
    } catch (e) {
      // ignore
    }
  },

  // 加载订单详情并组装详情页展示项
  loadOrder(id) {
    this.orderId = id
    api.getAppointmentById(id)
      .then((appt) => {
        const order = toOrder(appt)
        const items = [
          { label: '就诊医生', value: order.doctorName },
          { label: '就诊科室', value: order.departmentName },
          { label: '就诊时间', value: formatDateTime(order.scheduleDate, order.timeSlot) },
          { label: '就诊人', value: order.patientName },
          { label: '联系电话', value: order.patientPhone },
          { label: '挂号费用', value: money.fenToYuanLabel(order.feeFen) },
          { label: '订单编号', value: order.orderNo }
        ]
        if (order.payMethodText) {
          items.push({ label: '支付方式', value: order.payMethodText })
        }
        if (order.prescriptionAmountFen > 0) {
          items.push({ label: '处方费用', value: money.fenToYuanLabel(order.prescriptionAmountFen) })
        }
        this.setData({
          order,
          detailItems: items,
          feeText: money.fenToYuanLabel(order.feeFen),
          feeAmountText: money.fenToYuanText(order.feeFen),
          rxFeeText: money.fenToYuanLabel(order.prescriptionAmountFen)
        })
        this.loadRefund(order)
      })
      .catch((err) => toast(err.message || '订单不存在'))
  },

  // 加载退款卡片。
  // ★ 只对「已取消 + 第三方渠道付款」的订单显示：余额支付的退款在取消那一刻已经同步
  //   退回余额，后端根本不落 refund_order，没有可展示的东西。
  // ★ outTradeNo 只能从本地缓存拿（后端 AppointmentVO 不返回它），所以拿不到时
  //   如实提示「本机没有这张单的支付单号」，而不是假装没有退款。
  loadRefund(order) {
    const visible = !!order && order.rawStatus === 'cancelled' && isChannelPay(order.payMethod)
    if (!visible) {
      this.setData({ refundVisible: false, refund: null, refundTip: '', refundOutTradeNo: '' })
      return
    }

    const outTradeNo = getOutTradeNo('APPOINTMENT', order.id)
    this.setData({ refundVisible: true, refundOutTradeNo: outTradeNo })

    if (!outTradeNo) {
      this.setData({
        refund: null,
        refundTip: '本机没有这张单的支付单号记录，查不到退款进度。后端 AppointmentVO 目前不返回 outTradeNo，只有在本机发起过支付的订单才能查。'
      })
      return
    }

    api.getRefundOrder(outTradeNo)
      .then((refund) => {
        this.setData({
          refund: refund
            ? {
              ...refund,
              statusText: REFUND_STATUS_TEXT[refund.status] || refund.status,
              amountText: money.fenToYuanLabel(refund.amountFen)
            }
            : null,
          refundTip: refund ? '' : '支付单已进入退款流程，但还没有退款单落库，稍后刷新再看。'
        })
      })
      .catch((err) => {
        // 没退过款时后端返回 Result.fail("该支付单没有退款记录")，request 会 reject —— 这是正常分支
        this.setData({
          refund: null,
          refundTip: (err && err.message) || '该支付单没有退款记录'
        })
      })
  },

  // ⚠️ 测试专用：模拟微信网关回传「退款成功」通知。
  // 后端收到后会走 /api/pay/refund-notify 同一条链路：refund_order PROCESSING→SUCCESS、
  // payment_order REFUNDING→REFUNDED。真实微信里这一步由网关异步推送，前端没有这个按钮。
  async onMockRefundSuccess() {
    if (this.data.refundBusy) return
    const { refundOutTradeNo, order } = this.data
    if (!refundOutTradeNo || !order) return
    this.setData({ refundBusy: true })
    try {
      await api.mockRefundSuccess(refundOutTradeNo)
      wx.showToast({ title: '已模拟退款成功', icon: 'success' })
    } catch (err) {
      toast(err.message || '模拟退款成功失败')
    }
    this.setData({ refundBusy: false })
    this.loadRefund(order)
  },

  // ⚠️ 测试专用：模拟微信网关回传「退款失败」通知。
  // 后端会把 refund_order 置 FAILED、payment_order 从 REFUNDING 回退成 PAID。
  // ★ 但这只是短暂状态：对账任务每 60s 扫一次「已收款但业务单已取消」，会重新发起退款。
  async onMockRefundFail() {
    if (this.data.refundBusy) return
    const { refundOutTradeNo, order } = this.data
    if (!refundOutTradeNo || !order) return
    this.setData({ refundBusy: true })
    try {
      await api.mockRefundFail(refundOutTradeNo)
      wx.showModal({
        title: '已模拟退款失败',
        content: '退款单已标记失败，支付单回退为「已支付」。注意：后端的对账任务每 60 秒会把「已收款但业务单已取消」的单重新发起退款，所以一两分钟内状态会自己回到「退款处理中」。',
        showCancel: false
      })
    } catch (err) {
      toast(err.message || '模拟退款失败失败')
    }
    this.setData({ refundBusy: false })
    this.loadRefund(order)
  },

  // 选择支付方式，若需二次确认则弹出支付抽屉
  onPayMethodTap(e) {
    const { id } = e.currentTarget.dataset
    if (!id) return
    // 支付宝入口这轮只做占位：保留位置让用户知道后面会接，但不发起任何支付
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

  // 点击支付按钮，校验状态后直接支付或先弹确认抽屉
  onPay() {
    if (this.data.paying || !this.data.order || !this.data.order.canPay) return
    const { payMethod } = this.data
    if (needsPayDrawer(payMethod)) {
      this.setData({ payDrawerVisible: true, payDrawerMethod: payMethod })
      return
    }
    this.doPay()
  },

  // 发起支付。两条完全不同的链路：
  //   余额   → POST /api/appointments/{id}/pay，后端在同一事务里扣款并把预约推进到 pending，一步到位
  //   微信/支付宝 → 先 prepay 建支付单（预约仍是 unpaid），再由用户在模拟收银台确认，
  //                回调落到后端后才真正推进业务状态
  async doPay() {
    if (this.data.paying || !this.data.order || !this.data.order.canPay) return
    const { payMethod, order, balanceFen } = this.data

    if (isBalancePay(payMethod)) {
      // 挂号费与余额都在「分」域内比较，不引入元也不引入浮点
      if (order.feeFen > balanceFen) {
        toast('余额不足，请先充值')
        return
      }
      this.setData({ paying: true })
      try {
        await api.payAppointment(order.id, payMethod)
        this.setData({ payDrawerVisible: false })
        wx.showToast({ title: '支付成功', icon: 'success' })
        this.loadOrder(order.id)
        this.loadBalance()
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
      const prepay = await api.prepayAppointment(order.id, payMethod)
      // 支付单号要留下来：之后取消这张预约时，后端发起退款、前端查退款进度、
      // Mock 退款回调全都得靠它（后端不通过 AppointmentVO 回传这个号）
      saveOutTradeNo('APPOINTMENT', order.id, prepay.outTradeNo)
      this.setData({
        paying: false,
        payDrawerVisible: false,
        paySheetVisible: true,
        paySheetBusy: false,
        paySheetMethod: payMethod,
        paySheetOutTradeNo: prepay.outTradeNo,
        paySheetDescription: `挂号费-${order.patientName || ''}`,
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
    const { paySheetBusy, paySheetOutTradeNo, order } = this.data
    if (paySheetBusy || !paySheetOutTradeNo || !order) return
    this.setData({ paySheetBusy: true })

    let payment = null
    try {
      await api.mockPaySuccess(paySheetOutTradeNo)
      payment = await pollPaymentOrder(paySheetOutTradeNo)
    } catch (err) {
      // 回调投递失败或轮询超时：两者对用户来说是同一件事 —— 结果未确认，去订单列表刷新
      this.closePaySheet()
      toast(err.message || '支付结果确认失败，请稍后在订单列表刷新查看')
      this.loadOrder(order.id)
      return
    }

    this.closePaySheet()
    if (payment.status === 'PAID') {
      wx.showToast({ title: '支付成功', icon: 'success' })
    } else {
      toast(`${PAYMENT_STATUS_TEXT[payment.status] || '支付未完成'}，请稍后刷新订单`)
    }
    this.loadOrder(order.id)
    this.loadBalance()
  },

  // 收银台「取消」：只收起本次支付行为，绝不去动后端支付单。
  // 后端 /mock/pay/{outTradeNo}/cancel 会 closeIfCreated → AppointmentPayHandler.onPayClosed，
  // 那会把预约 unpaid → cancelled 并且回补号源，语义上等于「取消预约」，不是「这次先不付」。
  onPaySheetCancel() {
    if (this.data.paySheetBusy) return
    this.closePaySheet()
    toast('已取消本次支付，预约仍然有效')
  },

  // ⚠️ 测试专用：模拟支付超时。
  // 它会真正走后端关单逻辑（先网关关单、再 CAS、再 onPayClosed），
  // 所以结果是**真的**把预约取消掉并释放号源 —— 这正是这条链路要验证的东西。
  async onPaySheetMockTimeout() {
    const { paySheetBusy, paySheetOutTradeNo, order } = this.data
    if (paySheetBusy || !paySheetOutTradeNo || !order) return
    this.setData({ paySheetBusy: true })
    try {
      await api.mockPayClose(paySheetOutTradeNo)
      this.closePaySheet()
      wx.showModal({
        title: '已模拟支付超时',
        content: '支付单已关闭，预约已取消并释放号源。这是关单链路的真实执行结果，不是模拟提示。',
        showCancel: false
      })
      this.loadOrder(order.id)
    } catch (err) {
      this.setData({ paySheetBusy: false })
      toast(err.message || '模拟支付超时失败')
    }
  },

  // ⚠️ 测试专用：模拟支付失败。纯前端行为，不发任何请求，
  // 因此支付单保持 CREATED、预约仍是 unpaid；用户重新点支付时后端 prepay 有可重入护栏，
  // 会复用同一张支付单，不会堆出多张单。
  onPaySheetMockFail() {
    if (this.data.paySheetBusy) return
    this.closePaySheet()
    toast('已模拟支付失败（未请求后端，可重新发起支付）')
  },

  // 弹窗确认后取消该预约，并刷新订单状态
  onCancel() {
    wx.showModal({
      title: '取消预约',
      content: '确定要取消该预约吗？',
      success: (res) => {
        if (res.confirm) {
          api.cancelAppointment(this.data.order.id)
            .then(() => {
              // loadOrder 里会连带刷新退款卡片：已用微信支付的单此时应变成「退款处理中」
              this.loadOrder(this.data.order.id)
              wx.showToast({ title: '已取消', icon: 'success' })
            })
            .catch((err) => toast(err.message || '取消失败'))
        }
      }
    })
  },

  // 跳转到订单评价页面
  onEvaluate() {
    wx.navigateTo({ url: `/pages/order/evaluate?id=${this.data.order.id}` })
  },

  // 点击签到，调用签到接口并刷新订单
  onCheckIn() {
    const { order } = this.data
    api.checkInAppointment(order.id)
      .then(() => {
        wx.showToast({ title: '签到成功', icon: 'success' })
        this.loadOrder(order.id)
      })
      .catch((err) => toast(err.message || '签到失败'))
  },

  // 跳转到就诊病历详情页
  onViewRecord() {
    wx.navigateTo({ url: `/pages/visit/record?id=${this.data.order.id}` })
  },

  // 跳转到处方详情页，可缴费时额外带上处方 id
  onViewPrescription() {
    const { order } = this.data
    if (order.canPayPrescription) {
      wx.navigateTo({ url: `/pages/prescription/detail?id=${order.prescriptionId}&appointmentId=${order.id}` })
    } else {
      wx.navigateTo({ url: `/pages/prescription/detail?appointmentId=${order.id}` })
    }
  },

  // 查看处方（兼容旧模板调用，转发到 onViewPrescription）
  onPrescription() {
    this.onViewPrescription()
  },

  // 跳转到余额充值页面
  onRecharge() {
    wx.navigateTo({ url: '/pages/recharge/recharge' })
  }
})
