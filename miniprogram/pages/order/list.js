const { setTabBar } = require('../../utils/tab')
const auth = require('../../utils/auth')
const api = require('../../utils/api')
const { toOrder } = require('../../utils/adapter')
const toast = require('../../utils/toast')

// 判断订单是否属于指定状态 tab（含待就诊、待取药等特殊归类规则）
function matchOrderTab(order, tabKey) {
  if (tabKey === 'all') return true
  const status = order.rawStatus || order.status
  const display = order.displayStatus || order.status
  if (tabKey === 'pending') {
    return status === 'pending' || status === 'confirmed'
  }
  if (tabKey === 'pending_dispense') {
    return display === 'pending_dispense'
  }
  if (tabKey === 'pending_rx_pay') {
    return status === 'pending_rx_pay' && display !== 'pending_dispense'
  }
  return display === tabKey || status === tabKey
}

Page({
  data: {
    activeTab: 0,
    tabs: [
      { key: 'all', title: '全部' },
      { key: 'unpaid', title: '待支付' },
      { key: 'pending', title: '待就诊' },
      { key: 'checked_in', title: '已到院' },
      { key: 'in_consultation', title: '就诊中' },
      { key: 'pending_rx_pay', title: '待缴药费' },
      { key: 'pending_dispense', title: '待取药' },
      { key: 'completed', title: '已完成' },
      { key: 'cancelled', title: '已取消' }
    ],
    orders: [],
    loggedIn: false
  },

  // 页面每次显示时设置 tab 高亮，登录后加载订单列表
  onShow() {
    setTabBar(this, '/pages/order/list')
    const loggedIn = auth.isLoggedIn()
    this.setData({ loggedIn })
    if (!loggedIn) {
      this.setData({ orders: [] })
      return
    }
    this.loadOrders()
  },

  // 未登录时点击跳转到"我的"页面登录
  onGoLogin() {
    wx.switchTab({ url: '/pages/mine/mine' })
  },

  // 按当前 tab 对应状态拉取订单列表并做前端过滤
  loadOrders(tabIndex) {
    const index = tabIndex != null ? tabIndex : this.data.activeTab
    const key = this.data.tabs[index].key
    const params = { current: 1, size: 50 }
    if (key !== 'all') {
      params.status = key
    }
    api.getAppointments(params)
      .then((page) => {
        const orders = (page.records || [])
          .map(toOrder)
          .filter((order) => matchOrderTab(order, key))
        this.setData({ orders })
      })
      .catch((err) => toast(err.message || '加载失败'))
  },

  // 切换状态 tab 并按新 tab 重新加载订单
  onTabChange(e) {
    const { index, name } = e.detail || {}
    let tabIndex = typeof index === 'number' ? index : this.data.activeTab
    if (name) {
      const found = this.data.tabs.findIndex((tab) => tab.key === name)
      if (found >= 0) tabIndex = found
    }
    this.setData({ activeTab: tabIndex })
    this.loadOrders(tabIndex)
  },

  // 点击订单卡片跳转到订单详情页
  onOrderTap(e) {
    wx.navigateTo({ url: `/pages/order/detail?id=${e.detail.id}` })
  },

  // 处理订单卡片上的操作按钮（支付、取消、评价、处方）
  onOrderAction(e) {
    const { action, id } = e.detail
    if (action === 'pay') {
      wx.navigateTo({ url: `/pages/order/detail?id=${id}` })
      return
    }
    if (action === 'cancel') {
      wx.showModal({
        title: '取消预约',
        content: '确定要取消该预约吗？',
        success: (res) => {
          if (res.confirm) {
            api.cancelAppointment(id)
              .then(() => {
                wx.showToast({ title: '已取消', icon: 'success' })
                this.loadOrders()
              })
              .catch((err) => toast(err.message || '取消失败'))
          }
        }
      })
    } else if (action === 'evaluate') {
      wx.navigateTo({ url: `/pages/order/evaluate?id=${id}` })
    } else if (action === 'rx') {
      wx.navigateTo({ url: `/pages/order/detail?id=${id}` })
    }
  }
})
