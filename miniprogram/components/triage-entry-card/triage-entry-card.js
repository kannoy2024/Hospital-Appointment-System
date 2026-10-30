Component({
  methods: {
    // 点击卡片跳转到智能分诊页面
    onTap() {
      wx.navigateTo({ url: '/pages/triage/index' })
    }
  }
})
