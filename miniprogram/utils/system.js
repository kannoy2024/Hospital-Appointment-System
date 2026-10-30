// 获取状态栏高度（px），获取失败时返回默认 20
function getStatusBarHeight() {
  try {
    return wx.getWindowInfo().statusBarHeight || 20
  } catch (e) {
    return 20
  }
}

// 获取导航栏总高度（状态栏 + 88rpx 换算后的内容区），获取失败时返回默认 64
function getNavBarHeight() {
  try {
    const win = wx.getWindowInfo()
    const navContentPx = Math.round(88 * win.windowWidth / 750)
    return (win.statusBarHeight || 20) + navContentPx
  } catch (e) {
    return 64
  }
}

module.exports = { getStatusBarHeight, getNavBarHeight }
