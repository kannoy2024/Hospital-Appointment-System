const { getStatusInfo } = require('../../utils/format')

Component({
  properties: {
    status: { type: String, value: '' },
    location: { type: String, value: '' }
  },
  observers: {
    // 监听 status 属性变化，派生订单状态展示文案
    status(s) {
      const info = getStatusInfo(s)
      this.setData({ statusText: info.text })
    }
  },
  data: { statusText: '' }
})
