/**
 * 模拟收银台
 *
 * 它扮演的是「微信/支付宝那边弹出的支付确认页」。
 *
 * 为什么需要它：后端 prepay 在 mock 通道下返回的 payParams 只有
 * { outTradeNo, cashierUrl }，没有 appId/timeStamp/nonceStr/package/signType/paySign，
 * 所以 wx.requestPayment 根本调不起来（会直接报参数错误）。
 * 而 <web-view> 又不能加载 http://127.0.0.1 这种本地地址（业务域名白名单 + 强制 HTTPS），
 * 因此只能在原生层自绘一个收银台。
 *
 * 组件本身不发请求：它只负责「让用户做出选择」并把结果抛给页面，
 * 真正的网络调用（mockPaySuccess / mockPayClose / 轮询支付单）全部留在页面里，
 * 这样切真实微信支付时只需要改页面，组件可以直接删。
 */
const { getPayChannelInfo } = require('../../utils/pay')

Component({
  properties: {
    show: { type: Boolean, value: false },
    payMethod: { type: String, value: 'wechat' },
    outTradeNo: { type: String, value: '' },
    description: { type: String, value: '' },
    // 元文本，不带 ¥，组件自己补符号（与 pay-drawer 保持一致）
    amountText: { type: String, value: '0.00' },
    // 支付截止时间（已格式化好的字符串）
    expireText: { type: String, value: '' },
    // 取消按钮文案：每个业务的「取消」后果不同，由页面说清楚
    cancelText: { type: String, value: '取消（只关闭本次支付）' },
    // 测试按钮的说明文案：同样因业务而异（挂号会取消预约，处方/充值不会）
    mockTimeoutDesc: { type: String, value: '关闭支付单，业务状态按后端规则处理' },
    mockFailDesc: { type: String, value: '不请求后端，支付单保持待支付，可重新发起' },
    // 请求进行中：锁住所有按钮，避免重复发起
    busy: { type: Boolean, value: false }
  },

  data: {
    channelName: '微信支付',
    brandChar: '微',
    brandClass: 'wechat'
  },

  observers: {
    // 支付方式变化时同步品牌色与名称
    payMethod(method) {
      this.applyChannel(method)
    }
  },

  lifetimes: {
    attached() {
      this.applyChannel(this.data.payMethod)
    }
  },

  methods: {
    // 按支付方式切换渠道名称与配色（本轮 alipay 只是占位，不会被走到）
    applyChannel(method) {
      const info = getPayChannelInfo(method)
      this.setData({
        channelName: info.name,
        brandChar: method === 'alipay' ? '支' : '微',
        brandClass: method === 'alipay' ? 'alipay' : 'wechat'
      })
    },

    // 关闭 / 取消：只通知页面收起弹窗，页面不会去动支付单
    onClose() {
      if (this.data.busy) return
      this.triggerEvent('cancel')
    },

    // 用户确认支付
    onConfirm() {
      if (this.data.busy) return
      this.triggerEvent('confirm')
    },

    // 测试专用：走后端关单逻辑（会连带取消预约）
    onMockTimeout() {
      if (this.data.busy) return
      this.triggerEvent('mocktimeout')
    },

    // 测试专用：纯前端失败，不碰后端
    onMockFail() {
      if (this.data.busy) return
      this.triggerEvent('mockfail')
    }
  }
})
