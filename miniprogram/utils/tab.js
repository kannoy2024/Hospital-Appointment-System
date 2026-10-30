const TAB_INDEX = {
  '/pages/index/index': 0,
  '/pages/schedule/list': 1,
  '/pages/order/list': 2,
  '/pages/mine/mine': 3
}

// 根据页面路径设置自定义 tabBar 的选中项
function setTabBar(page, path) {
  if (typeof page.getTabBar !== 'function') return
  const tabBar = page.getTabBar()
  if (!tabBar) return
  const active = TAB_INDEX[path]
  if (active !== undefined) {
    tabBar.setData({ active })
  }
}

// 携带待选科室/关键词跳转到排班 Tab 页
function goDeptTab(options = {}) {
  const app = getApp()
  if (options.deptId) {
    app.globalData.pendingDeptId = Number(options.deptId)
  }
  if ('keyword' in options) {
    app.globalData.pendingKeyword = (options.keyword || '').trim()
  }
  wx.switchTab({ url: '/pages/schedule/list' })
}

module.exports = { setTabBar, goDeptTab, TAB_INDEX }
