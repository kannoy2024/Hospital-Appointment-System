const auth = require('./utils/auth')
const { setupPrivacyAuthorization } = require('./utils/privacy')

App({
  globalData: {
    token: '',
    user: null,
    triageSymptom: '',
    triageResult: null,
    pendingDeptId: 0,
    pendingKeyword: ''
  },

  // 小程序启动时触发，初始化隐私授权、恢复本地登录态并校验 token
  onLaunch() {
    setupPrivacyAuthorization()

    const token = auth.getToken()
    const user = auth.getUser()
    if (token) {
      this.globalData.token = token
      this.globalData.user = user
      // 校验 token 是否仍有效；失败时保留本地登录态，避免因网络/后端未启动误踢下线。
      // 真正的 401 登录过期已由 request.js 内部调用 sessionExpired() 清理。
      auth.fetchMe().catch(() => {})
    } else if (auth.shouldAutoLogin()) {
      auth.wxLogin().catch(() => {})
    }
  }
})
