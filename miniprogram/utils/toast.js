// 显示无图标的轻提示，内容为空时直接返回
function toast(message) {
  if (message == null || message === '') {
    return
  }
  wx.showToast({
    title: String(message),
    icon: 'none',
    duration: 2000
  })
}

// 显示带遮罩的加载中提示，默认文案为「加载中」
toast.loading = (message) => {
  wx.showLoading({ title: String(message || '加载中'), mask: true })
}

// 显示带成功图标的提示
toast.success = (message) => {
  wx.showToast({ title: String(message), icon: 'success' })
}

// 关闭所有 toast 与 loading 提示
toast.clear = () => {
  wx.hideToast()
  wx.hideLoading()
}

module.exports = toast
