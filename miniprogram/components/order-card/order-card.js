const { getStatusInfo, formatDateTime } = require('../../utils/format')
const money = require('../../utils/money')

Component({
  properties: {
    order: { type: Object, value: {} }
  },
  observers: {
    // 监听 order 属性变化，派生状态文案、类型、就诊时间与费用等展示字段
    order(o) {
      if (!o) return
      const status = getStatusInfo(o.displayStatus || o.rawStatus || o.status)
      this.setData({
        statusText: status.text,
        statusType: status.type,
        timeText: formatDateTime(o.scheduleDate, o.timeSlot),
        feeText: money.fenToYuanLabel(o.feeFen)
      })
    }
  },
  data: {
    statusText: '',
    statusType: 'default',
    timeText: '',
    feeText: ''
  },
  methods: {
    // 点击订单卡片时向父组件抛出 tap 事件并带上订单 id
    onTap() {
      this.triggerEvent('tap', { id: this.data.order.id })
    },
    // 点击订单操作按钮时向父组件抛出 action 事件并带上操作类型与订单 id
    onAction(e) {
      const { action } = e.currentTarget.dataset
      this.triggerEvent('action', { action, id: this.data.order.id })
    }
  }
})
