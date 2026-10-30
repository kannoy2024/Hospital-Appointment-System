Component({
  data: {
    active: 0,
    routes: [
      '/pages/index/index',
      '/pages/schedule/list',
      '/pages/order/list',
      '/pages/mine/mine'
    ]
  },

  methods: {
    // 切换底部 tab 时触发，按索引跳转到对应页面
    onChange(e) {
      const idx = Number(e.detail)
      const url = this.data.routes[idx]
      if (!url || idx === this.data.active) return
      wx.switchTab({ url })
    }
  }
})
