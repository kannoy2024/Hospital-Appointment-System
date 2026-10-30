const api = require('../../../utils/api')
const auth = require('../../../utils/auth')
const toast = require('../../../utils/toast')
const { formatServerDateTime } = require('../../../utils/format')

Page({
  data: {
    loading: false,
    list: []
  },

  // 页面显示时校验登录状态，已登录则加载通知列表
  onShow() {
    if (!auth.isLoggedIn()) {
      toast('请先登录')
      wx.switchTab({ url: '/pages/mine/mine' })
      return
    }
    this.loadList()
  },

  // 拉取通知列表并格式化创建时间
  loadList() {
    this.setData({ loading: true })
    api.getNotifications({ current: 1, size: 50 })
      .then((page) => {
        const list = (page.records || []).map((item) => ({
          ...item,
          timeText: formatServerDateTime(item.createTime)
        }))
        this.setData({ list })
      })
      .catch((err) => toast(err.message || '加载失败'))
      .finally(() => this.setData({ loading: false }))
  },

  // 点击全部已读，标记所有通知为已读并刷新列表
  onReadAll() {
    api.markAllNotificationsRead()
      .then(() => {
        wx.showToast({ title: '已全部已读', icon: 'success' })
        this.loadList()
      })
      .catch((err) => toast(err.message || '操作失败'))
  },

  // 点击某条通知，标记为已读并按业务类型跳转到对应详情页
  onItemTap(e) {
    const { item } = e.currentTarget.dataset
    if (!item) return
    const go = () => {
      if (item.bizType === 'appointment' && item.bizId) {
        wx.navigateTo({ url: `/pages/order/detail?id=${item.bizId}` })
      }
    }
    if (!item.read) {
      api.markNotificationRead(item.id)
        .then(go)
        .catch(go)
    } else {
      go()
    }
  }
})
