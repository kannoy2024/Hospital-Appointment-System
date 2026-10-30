const api = require('../../../utils/api')
const toast = require('../../../utils/toast')
const auth = require('../../../utils/auth')

Page({
  data: {
    patients: [],
    loading: true
  },

  // 页面显示时触发：校验登录状态并刷新就诊人列表（从新增/编辑页返回时也会刷新）
  onShow() {
    if (!auth.isLoggedIn()) {
      toast('请先登录')
      setTimeout(() => wx.navigateBack(), 800)
      return
    }
    this.loadPatients()
  },

  // 拉取就诊人列表并做展示字段加工
  loadPatients() {
    this.setData({ loading: true })
    api.getPatients()
      .then((list) => {
        this.setData({
          patients: (list || []).map(this.decorate),
          loading: false
        })
      })
      .catch((err) => {
        toast(err.message || '加载失败')
        this.setData({ loading: false })
      })
  },

  // 把后端字段加工成列表直接能显示的字段
  decorate(p) {
    return {
      ...p,
      genderText: p.gender === 2 ? '女' : (p.gender === 1 ? '男' : '未知'),
      idCardMasked: maskIdCard(p.idCard),
      isDefault: p.isDefault === 1
    }
  },

  // 点击新增按钮，跳转到就诊人新增页
  onAdd() {
    wx.navigateTo({ url: '/pages/mine/patient/edit' })
  },

  // 点击编辑，携带就诊人 id 跳转到编辑页
  onEdit(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/mine/patient/edit?id=${id}` })
  },

  // 将指定就诊人设为默认，并重新加载列表
  onSetDefault(e) {
    const { id } = e.currentTarget.dataset
    const target = this.data.patients.find((p) => String(p.id) === String(id))
    if (!target || target.isDefault) return   // 已经是默认,不用重复设置
    api.setDefaultPatient(id)
      .then(() => {
        toast.success('已设为默认')
        this.loadPatients()
      })
      .catch((err) => toast(err.message || '设置失败'))
  },

  // 弹窗二次确认后删除指定就诊人并刷新列表
  onDelete(e) {
    const { id, name } = e.currentTarget.dataset
    wx.showModal({
      title: '删除就诊人',
      content: `确定删除「${name}」吗？`,
      confirmColor: '#FF4D4F',
      success: (res) => {
        if (!res.confirm) return
        api.deletePatient(id)
          .then(() => {
            toast.success('已删除')
            this.loadPatients()
          })
          .catch((err) => toast(err.message || '删除失败'))
      }
    })
  }
})

// 身份证脱敏：只留前4后4
function maskIdCard(idCard) {
  if (!idCard || idCard.length < 8) return idCard || ''
  return idCard.replace(/^(.{4}).*(.{4})$/, '$1**********$2')
}