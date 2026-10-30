const api = require('../../utils/api')
const auth = require('../../utils/auth')
const toast = require('../../utils/toast')
const { setTabBar } = require('../../utils/tab')
const { resolveMediaUrl } = require('../../utils/media')
const money = require('../../utils/money')

Page({

  /**
   * 页面的初始数据
   */
  data: {
    loggedIn: false,
    user: null,
    displayAvatar: '',
    balanceText: '',
    wxNickname: '',
    wxPhone: '',
    wxAvatarPath: '',
    wxLoginLoading: false,
    profileSyncing: false,
    unreadCount: 0,
    // 完善资料弹窗
    profileDialogVisible: false,
    dialogName: '',
    dialogPhone: '',
    dialogEmail: '',
    submittingProfile: false,
    menuItems: [
      { icon: 'bell', title: '消息通知', url: '/pages/mine/notifications/notifications', badge: true },
      { icon: 'balance-o', title: '余额充值', url: '/pages/recharge/recharge' },
      { icon: 'like-o', title: '医生收藏', url: '/pages/mine/favorite_doctor/favorite_doctor' },
      { icon: 'orders-o', title: '我的订单', url: '/pages/order/list', tab: true },
      { icon: 'friends-o', title: '就诊人档案', url: '/pages/mine/patient/list' },
      { icon: 'description', title: '用户协议', url: '/pages/mine/agreement/agreement' },
      { icon: 'shield-o', title: '隐私政策', url: '/pages/mine/privacy/privacy' },
      { icon: 'info-o', title: '关于我们', url: '/pages/mine/about/about' }
    ]
  },


  /**
   * 生命周期函数--监听页面显示
   */
  onShow() {
    setTabBar(this, '/pages/mine/mine')
    this.syncUser()
    this.loadUnread()
  },

  loadUnread() {
    if (!auth.isLoggedIn()) {
      this.setData({ unreadCount: 0 })
      return
    }
    api.getUnreadNotificationCount()
      .then((count) => this.setData({ unreadCount: count || 0 }))
      .catch(() => { })
  },


  // 刷新用户状态
  syncUser() {
    const user = auth.getUser()
    const avatar = user && user.avatar ? resolveMediaUrl(user.avatar) : ''
    // 余额真值是 user.balanceFen（单位分），未登录时留空以隐藏 mine.wxml 的余额行
    const balanceText = user && user.balanceFen != null ? money.fenToYuanText(user.balanceFen) : ''
    this.setData({
      loggedIn: auth.isLoggedIn(),
      user,
      displayAvatar: this.data.wxAvatarPath || avatar,
      balanceText,
      wxNickname: user ? (user.nickname || '') : this.data.wxNickname,
      wxPhone: user ? (user.phone || '') : this.data.wxPhone
    })
  },

  onChooseAvatar(e) {
    const path = e.detail.avatarUrl
    if (!path) return
    this.setData({
      wxAvatarPath: path,
      displayAvatar: path
    })
    if (this.data.loggedIn) {
      this.syncProfile({ avatarPath: path })
    }
  },

  isValidPhone(phone) {
    return /^1[3-9]\d{9}$/.test(phone || '')
  },

  onProfileCardTap() {
    this.onProfileDialogOpen()
  },

  // ========== 完善账号资料弹窗 ==========


  onProfileDialogOpen() {
    const user = auth.getUser() || {}
    this.setData({
      dialogName: user.nickname || '',
      dialogPhone: user.phone || '',
      dialogEmail: user.email || '',
      profileDialogVisible: true
    })
  },

  onDialogNameInput(e) {
    this.setData({ dialogName: e.detail })
  },

  onDialogPhoneInput(e) {
    this.setData({ dialogPhone: e.detail })
  },


  onDialogEmailInput(e) {
    this.setData({ dialogEmail: e.detail })
  },

  onProfileDialogConfirm() {
    if (this.data.submittingProfile) return
    const nickname = (this.data.dialogName || '').trim()
    const phone = (this.data.dialogPhone || '').trim()
    const email = (this.data.dialogEmail || '').trim()

    if (!nickname) {
      toast('请填写昵称')
      return
    }
    // 电话选填：填了才校验格式
    if (phone && !this.isValidPhone(phone)) {
      toast('请输入正确的手机号')
      return
    }
    // 邮箱选填：填了才校验格式
    if (email && !/^[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}$/.test(email)) {
      toast('邮箱格式不正确')
      return
    }

    this.setData({ submittingProfile: true })
    auth.syncWxProfile({ nickname, phone, email })
      .then(() => {
        this.setData({ profileDialogVisible: false })
        this.syncUser()
        toast.success('账号信息已更新')
      })
      .catch((err) => toast(err.message || '保存失败'))
      .finally(() => this.setData({ submittingProfile: false }))
  },

  syncProfile(payload) {
    if (this.data.profileSyncing) return
    this.setData({ profileSyncing: true })
    auth.syncWxProfile(payload)
      .then(() => {
        this.setData({ wxAvatarPath: '' })
        this.syncUser()
        toast.success('资料已更新')
      })
      .catch((err) => toast(err.message || '资料更新失败'))
      .finally(() => this.setData({ profileSyncing: false }))
  },

  //微信登录的方法
  onWxLogin() {
    if (this.data.wxLoginLoading) return
    //更新微信登录的状态
    this.setData({ wxLoginLoading: true })
    // 使用auth中的wxlogin函数登录
    auth.wxLogin({
      nickname: this.data.wxNickname,
      avatarPath: this.data.wxAvatarPath || ''
    })
      .then(() => {
        toast.success('登录成功')
        this.syncUser()
      })
      .catch((err) => toast(err.message || '微信登录失败'))
      .finally(() => this.setData({ wxLoginLoading: false }))
  },
  //账号信息关闭的弹窗方法
  onProfileDialogClose() {
    if (this.data.submittingProfile) return
    this.setData({ profileDialogVisible: false })
  },

  onLogout() {
    auth.logout()
    this.setData({ wxNickname: '', wxAvatarPath: '', displayAvatar: '' })
    this.syncUser()
    toast('已退出登录')
  },

  onMenuTap(e) {
    const { item } = e.currentTarget.dataset
    if (item.tab) {
      wx.switchTab({ url: item.url })
    } else {
      wx.navigateTo({ url: item.url })
    }
  }
})
