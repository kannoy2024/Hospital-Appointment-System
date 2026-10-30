const api = require('../../../utils/api')
const toast = require('../../../utils/toast')

Page({
  data: {
    id: 0,
    isEdit: false,
    // 表单字段(扁平写法,和 mine 页弹窗风格一致)
    name: '',
    phone: '',
    idCard: '',
    gender: 1,        // 1男 2女
    birthDate: '',    // 'YYYY-MM-DD'
    address: '',
    marital: 2,       // 1已婚 2未婚
    today: '',
    submitting: false
  },

  // 页面加载时读取路由参数，判断新增或编辑并初始化表单
  onLoad(options) {
    const id = Number(options.id) || 0
    // 出生日期上限 = 今天
    const today = new Date().toISOString().slice(0, 10)
    this.setData({ id, isEdit: id > 0, today })

    if (id > 0) {
      wx.setNavigationBarTitle && null // 自定义导航,标题由 page-nav 控制
      this.loadPatient(id)
    }
  },

  // 按 id 拉取就诊人详情并回填表单
  loadPatient(id) {
    api.getPatientById(id)
      .then((p) => {
        this.setData({
          name: p.name || '',
          phone: p.phone || '',
          idCard: p.idCard || '',
          gender: p.gender === 2 ? 2 : 1,
          birthDate: p.birthDate || '',
          address: p.address || '',
          marital: p.marital === 1 ? 1 : 2
        })
      })
      .catch((err) => toast(err.message || '加载失败'))
  },

  // 姓名输入时更新表单
  onNameInput(e) { this.setData({ name: e.detail }) },
  // 联系电话输入时更新表单
  onPhoneInput(e) { this.setData({ phone: e.detail }) },
  // 身份证号输入时更新表单
  onIdCardInput(e) { this.setData({ idCard: e.detail }) },
  // 地址输入时更新表单
  onAddressInput(e) { this.setData({ address: e.detail }) },
  // 切换性别时更新表单
  onGenderChange(e) { this.setData({ gender: Number(e.detail) }) },
  // 切换婚姻状态时更新表单
  onMaritalChange(e) { this.setData({ marital: Number(e.detail) }) },
  // 选择出生日期时更新表单
  onBirthDateChange(e) { this.setData({ birthDate: e.detail.value }) },

  // 校验表单后提交，按新增或编辑调用对应接口保存就诊人
  onSubmit() {
    if (this.data.submitting) return

    const name = (this.data.name || '').trim()
    const phone = (this.data.phone || '').trim()
    const idCard = (this.data.idCard || '').trim()
    const address = (this.data.address || '').trim()

    // 前端校验(后端也会校验,这里是为了体验)
    if (!name) { toast('请填写就诊人姓名'); return }
    if (!phone) { toast('请填写联系电话'); return }
    if (!/^1[3-9]\d{9}$/.test(phone)) { toast('请输入正确的手机号'); return }
    if (idCard && !/^\d{17}[\dXx]$/.test(idCard)) { toast('身份证号格式不正确'); return }
    if (!this.data.marital) { toast('请选择婚姻状态'); return }

    const payload = {
      name,
      phone,
      idCard: idCard || null,
      gender: this.data.gender,
      birthDate: this.data.birthDate || null,
      address: address || null,
      marital: this.data.marital
    }

    this.setData({ submitting: true })
    const req = this.data.isEdit
      ? api.updatePatient(this.data.id, payload)
      : api.createPatient(payload)

    req
      .then(() => {
        toast.success(this.data.isEdit ? '已保存' : '已添加')
        setTimeout(() => wx.navigateBack(), 700)
      })
      .catch((err) => toast(err.message || '保存失败'))
      .finally(() => this.setData({ submitting: false }))
  }
})