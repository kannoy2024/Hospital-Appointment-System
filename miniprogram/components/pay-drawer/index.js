const { getPayChannelInfo } = require('../../utils/pay')

Component({
  properties: {
    show: { type: Boolean, value: false },
    method: { type: String, value: 'wechat' },
    amount: { type: String, value: '0.00' },
    balance: { type: String, value: '' },
    loading: { type: Boolean, value: false }
  },

  data: {
    channelName: ''
  },

  observers: {
    // 监听支付方式变化，同步展示对应的渠道名称
    method(method) {
      const info = getPayChannelInfo(method)
      this.setData({ channelName: info.name })
    }
  },

  lifetimes: {
    // 组件挂载时按当前支付方式初始化渠道名称
    attached() {
      const info = getPayChannelInfo(this.data.method)
      this.setData({ channelName: info.name })
    }
  },

  methods: {
    // 关闭支付抽屉时向父组件抛出 close 事件
    onClose() {
      this.triggerEvent('close')
    },

    // 确认支付时（非加载中）向父组件抛出 confirm 事件
    onConfirm() {
      if (this.data.loading) return
      this.triggerEvent('confirm')
    }
  }
})
