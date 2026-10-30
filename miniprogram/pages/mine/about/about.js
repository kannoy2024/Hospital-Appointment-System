const { aboutInfo } = require('../../../utils/legal-content')

Page({
  data: {
    info: aboutInfo
  },

  // 拨打医院联系电话
  onCallPhone() {
    const phone = this.data.info.hospital.phone
    if (!phone) return
    wx.makePhoneCall({ phoneNumber: phone.replace(/-/g, '') })
  },

  // 跳转到用户协议页面
  onOpenAgreement() {
    wx.navigateTo({ url: '/pages/mine/agreement/agreement' })
  },

  // 跳转到隐私政策页面
  onOpenPrivacy() {
    wx.navigateTo({ url: '/pages/mine/privacy/privacy' })
  }
})
