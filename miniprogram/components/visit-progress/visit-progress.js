const { VISIT_STEPS, getVisitStepIndex } = require('../../utils/format')

Component({
  properties: {
    status: { type: String, value: '' }
  },
  observers: {
    // 监听 status 属性变化，计算就诊进度步骤的完成/进行中状态及是否隐藏
    status(s) {
      const active = getVisitStepIndex(s)
      const steps = VISIT_STEPS.map((item, index) => ({
        ...item,
        done: active >= 0 && index < active,
        active: active >= 0 && index === active
      }))
      this.setData({ steps, hidden: active < 0 || s === 'unpaid' || s === 'cancelled' })
    }
  },
  data: {
    steps: [],
    hidden: true
  }
})
