// 注册隐私授权监听：需要授权时弹窗提示用户同意隐私指引
function setupPrivacyAuthorization() {
  if (typeof wx.onNeedPrivacyAuthorization !== 'function') {
    return
  }
  wx.onNeedPrivacyAuthorization((resolve) => {
    wx.showModal({
      title: '隐私提示',
      content: '部分能力需要您同意《用户隐私保护指引》后方可使用。',
      confirmText: '同意',
      cancelText: '拒绝',
      // 弹窗回调：用户点"同意"时返回授权通过
      success(res) {
        if (res.confirm) {
          resolve({ event: 'agree', buttonId: 'agree-privacy-btn' })
        }
      }
    })
  })
}

module.exports = { setupPrivacyAuthorization }
