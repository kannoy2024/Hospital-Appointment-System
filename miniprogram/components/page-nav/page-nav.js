Component({
  options: {
    addGlobalClass: true
  },
  properties: {
    title: {
      type: String,
      value: ''
    },
    back: {
      type: Boolean,
      value: true
    }
  },
  methods: {
    // 点击返回按钮时返回上一页，无上一页则切换回首页tab
    onBack() {
      if (!this.data.back) return
      wx.navigateBack({
        delta: 1,
        fail: () => {
          wx.switchTab({ url: '/pages/index/index' })
        }
      })
    }
  }
})
